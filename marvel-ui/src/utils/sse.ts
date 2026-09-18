import { ref } from 'vue'
import { useAuthStore } from '@/stores/auth'

/**
 * SSE 长连接客户端（fetch 流式读取，非原生 EventSource）。
 *
 * <p>原生 EventSource 不支持自定义请求头，而本项目鉴权走 Authorization: Bearer，
 * 故用 fetch + ReadableStream 手动解析 text/event-stream 帧：
 * 断线后按 3s 间隔自动重连，登出或收到 401 时停止。
 */
export interface SseEvent {
  event: string
  data: string
}

type Listener = (e: SseEvent) => void

/** 连接状态：用于调试与断线提示（connecting=正在建立/重连中） */
export const sseConnected = ref(false)

const listeners = new Set<Listener>()
let controller: AbortController | null = null
let retryTimer: number | undefined
let idleTimer: number | undefined
let lastEventAt = 0
let running = false

const RECONNECT_DELAY = 3000
/** 空闲阈值：服务端每 30s 发心跳，超过该时长没有任何事件即判定连接已静默死亡 */
const IDLE_TIMEOUT = 45000
/** 存活检查间隔 */
const IDLE_CHECK_INTERVAL = 5000

/** 记录最近一次收到数据的时间（心跳也算），供空闲看门狗判断连接是否还活着 */
function touch(): void {
  lastEventAt = Date.now()
}

/**
 * 空闲看门狗：服务端重启后 TCP 往往不会断开，读流既不报错也不结束，
 * 只靠“连接抛错”触发重连会永久卡住（角标不再更新）。超时即主动断开，
 * 由重连流程重新建立连接，并借 connected 事件补齐期间漏掉的未读数。
 */
function startIdleWatch(): void {
  stopIdleWatch()
  idleTimer = window.setInterval(() => {
    if (Date.now() - lastEventAt > IDLE_TIMEOUT) {
      controller?.abort()
    }
  }, IDLE_CHECK_INTERVAL)
}

function stopIdleWatch(): void {
  if (idleTimer) {
    window.clearInterval(idleTimer)
    idleTimer = undefined
  }
}

/** 注册事件监听，返回取消函数 */
export function onSseEvent(listener: Listener): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

function emit(e: SseEvent): void {
  listeners.forEach((fn) => fn(e))
}

/** 解析一批 SSE 帧：字段形如 "event: xxx" / "data: yyy"，帧间以空行分隔 */
function parseChunk(buffer: string): { events: SseEvent[]; rest: string } {
  const events: SseEvent[] = []
  // 以空行切帧；最后一段可能不完整，留到下一批
  const frames = buffer.split(/\r?\n\r?\n/)
  const rest = frames.pop() ?? ''
  for (const frame of frames) {
    let event = 'message'
    const dataLines: string[] = []
    for (const line of frame.split(/\r?\n/)) {
      if (line.startsWith('event:')) event = line.slice(6).trim()
      else if (line.startsWith('data:')) dataLines.push(line.slice(5).trimStart())
    }
    if (dataLines.length) events.push({ event, data: dataLines.join('\n') })
  }
  return { events, rest }
}

async function connect(): Promise<void> {
  const auth = useAuthStore()
  if (!auth.token || running) return
  running = true
  controller = new AbortController()

  try {
    const res = await fetch('/api/sse/subscribe', {
      headers: { Authorization: `Bearer ${auth.token}`, Accept: 'text/event-stream' },
      signal: controller.signal,
    })
    if (!res.ok || !res.body) {
      // 401：token 失效，交给登出流程，不再重连
      if (res.status === 401) {
        running = false
        return
      }
      throw new Error(`SSE 连接失败: ${res.status}`)
    }
    sseConnected.value = true
    touch()
    startIdleWatch()
    const reader = res.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    for (;;) {
      const { done, value } = await reader.read()
      if (done) break
      // 任何字节（含心跳帧）都算连接存活证据
      touch()
      buffer += decoder.decode(value, { stream: true })
      const { events, rest } = parseChunk(buffer)
      buffer = rest
      events.forEach(emit)
    }
  } catch (e) {
    // 主动 abort（登出/空闲看门狗/主动断开）不视为异常
    if ((e as Error)?.name !== 'AbortError') {
      // 网络中断：静默进入重连
    }
  } finally {
    sseConnected.value = false
    stopIdleWatch()
    running = false
    // 仍有 token 说明是意外断开：延迟重连
    if (useAuthStore().token) {
      retryTimer = window.setTimeout(() => void connect(), RECONNECT_DELAY)
    }
  }
}

/** 建立连接（幂等，重复调用无副作用） */
export function startSse(): void {
  if (retryTimer) {
    window.clearTimeout(retryTimer)
    retryTimer = undefined
  }
  void connect()
}

/** 断开连接（登出时调用，停止自动重连） */
export function stopSse(): void {
  if (retryTimer) {
    window.clearTimeout(retryTimer)
    retryTimer = undefined
  }
  stopIdleWatch()
  controller?.abort()
  controller = null
  running = false
  sseConnected.value = false
}
