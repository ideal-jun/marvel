<template>
  <!-- 站内消息：铃铛下拉面板（v-menu），打开即加载最近消息；
       点击外部自动关闭，点击条目标记已读但不收起面板 -->
  <v-menu v-model="menuOpen" location="bottom" offset="6" width="380" :close-on-content-click="false" @update:model-value="onMenuToggle">
    <template #activator="{ props: menuProps }">
      <v-tooltip text="站内消息" location="bottom">
        <template #activator="{ props: tipProps }">
          <!-- badge 必须包在 v-btn 外层：v-btn 自身 overflow:hidden，
               放在按钮内的 floating 角标溢出边缘会被裁剪显示不全 -->
          <v-badge
            :content="notice.unread"
            :model-value="notice.unread > 0"
            color="error"
            location="top right"
            offset-y="6" 
          >
            <v-btn v-bind="{ ...menuProps, ...tipProps }" variant="text" icon density="comfortable" rounded="lg">
              <v-icon icon="mdi-bell-outline" />
            </v-btn>
          </v-badge>
        </template>
      </v-tooltip>
    </template>

    <v-sheet rounded="lg">
      <div class="flex items-center px-4 py-3">
        <span class="text-body-2 font-weight-bold">站内消息</span>
        <v-chip v-if="notice.unread > 0" size="x-small" color="error" class="ml-2">{{ notice.unread }}</v-chip>
        <v-spacer />
        <v-btn
          variant="text"
          size="x-small"
          rounded="lg"
          :disabled="!notice.unread"
          prepend-icon="mdi-check-all"
          @click="onReadAll"
        >
          全部已读
        </v-btn>
      </div>
      <v-divider />

      <div class="notice-list">
        <v-list v-if="items.length" lines="two" class="py-0">
          <v-list-item v-for="item in items" :key="item.noticeId" class="px-4" @click="onRead(item)">
            <template #prepend>
              <v-icon
                :color="item.read ? 'grey' : 'primary'"
                :icon="item.read ? 'mdi-email-open-outline' : 'mdi-email-outline'"
              />
            </template>
            <v-list-item-title :class="item.read ? 'text-medium-emphasis' : 'font-weight-bold'">
              {{ item.title }}
            </v-list-item-title>
            <v-list-item-subtitle class="text-wrap">{{ item.content }}</v-list-item-subtitle>
            <template #append>
              <span class="text-caption text-medium-emphasis ml-2">{{ item.createTime }}</span>
            </template>
          </v-list-item>
        </v-list>
        <div v-else class="text-center text-medium-emphasis py-10">
          {{ loading ? '加载中…' : '暂无消息' }}
        </div>
      </div>

      <v-divider />
      <div class="flex items-center px-4 py-1">
        <span class="text-caption text-medium-emphasis">仅展示最近 20 条</span>
        <v-spacer />
        <v-btn variant="text" size="small" rounded="lg" append-icon="mdi-chevron-right" @click="goAll">查看全部</v-btn>
      </div>
    </v-sheet>
  </v-menu>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { http } from '@/api/request'
import { useNoticeStore } from '@/stores/notice'
import { onSseEvent } from '@/utils/sse'
import type { SseEvent } from '@/utils/sse'
import type { MyNoticeRow, PageResult } from '@/types/api'

const router = useRouter()
const notice = useNoticeStore()
const loading = ref(false)
const menuOpen = ref(false)
const items = ref<MyNoticeRow[]>([])
let offSse: (() => void) | undefined

async function loadList(): Promise<void> {
  loading.value = true
  try {
    const page = await http.get<PageResult<MyNoticeRow>>('/system/notice/my', {
      params: { pageNum: 1, pageSize: 20 },
    })
    items.value = page.records
  } catch {
    // 打开失败保持原列表
  } finally {
    loading.value = false
  }
}

/** 面板打开时刷新列表与未读数 */
function onMenuToggle(open: boolean): void {
  if (open) {
    void loadList()
    void notice.refreshUnread()
  }
}

/** 跳转到「我的消息」页查看全部消息（含分页与历史），并收起面板 */
function goAll(): void {
  menuOpen.value = false
  void router.push('/profile/message')
}

/**
 * SSE 推送处理：
 * notice=新公告（把推送内容插入列表预览，并回查未读数——角标始终以服务端为准，
 *   不做本地推算，避免重复投递或他端已读导致的计数漂移）；
 * connected=连接建立/重连成功（补一次未读数，覆盖断连期间漏掉的推送）
 */
function onPush(e: SseEvent): void {
  if (e.event === 'connected') {
    void notice.refreshUnread()
    return
  }
  if (e.event !== 'notice') return
  try {
    const incoming = JSON.parse(e.data) as MyNoticeRow
    if (incoming.noticeId && !items.value.some((i) => i.noticeId === incoming.noticeId)) {
      items.value = [{ ...incoming, read: false }, ...items.value].slice(0, 20)
    }
  } catch {
    // 帧内容异常：忽略预览，角标仍会刷新
  }
  void notice.refreshUnread()
}

async function onRead(item: MyNoticeRow): Promise<void> {
  if (item.read) return
  try {
    await notice.markRead(item.noticeId)
    item.read = true
  } catch {
    // 忽略单条失败
  }
}

async function onReadAll(): Promise<void> {
  try {
    await notice.markAllRead()
    await loadList()
  } catch {
    // 忽略
  }
}

/** 标签页重新可见时补一次未读数：浏览器会冻结后台标签页的长连接与定时器，回来时先对齐服务端状态 */
function onVisibilityChange(): void {
  if (document.visibilityState === 'visible') {
    void notice.refreshUnread()
  }
}

onMounted(() => {
  void notice.refreshUnread()
  offSse = onSseEvent(onPush)
  document.addEventListener('visibilitychange', onVisibilityChange)
})

onUnmounted(() => {
  offSse?.()
  document.removeEventListener('visibilitychange', onVisibilityChange)
})
</script>

<style scoped>
.notice-list {
  max-height: 420px;
  overflow-y: auto;
}
</style>
