<template>
  <div class="h-full overflow-auto">
    <div class="flex flex-col gap-4">
      <div class="flex items-center justify-between">
        <div class="text-h6">服务监控</div>
        <v-tooltip text="刷新" location="bottom">
          <template #activator="{ props }">
            <v-btn
              v-bind="props"
              icon="mdi-refresh"
              variant="text"
              density="comfortable"
              rounded="lg"
              :loading="loading"
              @click="load"
            />
          </template>
        </v-tooltip>
      </div>

      <v-row dense>
        <v-col v-for="s in sections" :key="s.title" cols="12" md="6">
          <v-card rounded="xl" variant="tonal" :title="s.title">
            <v-card-text>
              <v-table density="compact" class="bg-transparent">
                <tbody>
                  <tr v-for="([label, value], i) in s.rows" :key="i">
                    <td class="text-medium-emphasis" style="width: 150px">{{ label }}</td>
                    <td class="text-break">{{ value }}</td>
                  </tr>
                </tbody>
              </v-table>
            </v-card-text>
          </v-card>
        </v-col>

        <v-col cols="12" md="6">
          <v-card rounded="xl" variant="tonal" title="磁盘">
            <v-card-text>
              <v-table density="compact" class="bg-transparent">
                <thead>
                  <tr>
                    <th>路径</th>
                    <th>总容量</th>
                    <th>已用</th>
                    <th>可用</th>
                    <th>使用率</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(d, i) in diskRows" :key="i">
                    <td>{{ d.path }}</td>
                    <td>{{ d.total }}</td>
                    <td>{{ d.used }}</td>
                    <td>{{ d.usable }}</td>
                    <td>{{ d.usage }}</td>
                  </tr>
                </tbody>
              </v-table>
            </v-card-text>
          </v-card>
        </v-col>

        <v-col cols="12" md="6">
          <v-card rounded="xl" variant="tonal" title="Redis">
            <v-card-text>
              <v-table density="compact" class="bg-transparent">
                <tbody>
                  <tr v-for="([label, value], i) in redisRows" :key="i">
                    <td class="text-medium-emphasis" style="width: 150px">{{ label }}</td>
                    <td class="text-break">{{ value }}</td>
                  </tr>
                </tbody>
              </v-table>
            </v-card-text>
          </v-card>
        </v-col>
      </v-row>
    </div>

    <v-snackbar v-model="snack.show" :color="snack.color" timeout="3000">{{
      snack.text
    }}</v-snackbar>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { http } from '@/api/request'

interface DiskRow {
  path: string
  total: string
  used: string
  usable: string
  usage: string
}
interface ServerInfo {
  sys?: Record<string, unknown>
  cpu?: Record<string, unknown>
  memory?: Record<string, unknown>
  jvm?: Record<string, unknown>
  disk?: DiskRow[]
}

const LABELS: Record<string, string> = {
  osName: '操作系统',
  osArch: '系统架构',
  osVersion: '系统版本',
  hostName: '主机名',
  userDir: '项目路径',
  currentTime: '当前时间',
  cores: '核心数',
  systemLoadAverage: '系统负载',
  cpuLoad: 'CPU 使用率',
  processCpuLoad: '进程 CPU 使用率',
  total: '总量/总容量',
  used: '已用',
  free: '空闲',
  usage: '使用率',
  javaVersion: 'Java 版本',
  javaVendor: 'Java 厂商',
  jvmName: 'JVM',
  startTime: '启动时间',
  uptime: '运行时长',
  heapUsed: '堆已用',
  heapMax: '堆上限',
  heapUsage: '堆使用率',
  nonHeapUsed: '非堆已用',
  threadCount: '线程数',
  peakThreadCount: '峰值线程数',
  version: '版本',
  mode: '模式',
  uptimeDays: '运行天数',
  connectedClients: '连接数',
  usedMemory: '已用内存',
  maxMemory: '内存上限',
  totalCommands: '累计命令',
  expiredKeys: '过期键',
  keyspaceHits: '命中',
  keyspaceMisses: '未命中',
  keyspace: 'Keyspace',
  usable: '可用',
  path: '路径',
  error: '错误',
}

const loading = ref(false)
const server = ref<ServerInfo>({})
const redis = ref<Record<string, unknown>>({})
const snack = reactive({ show: false, text: '', color: 'error' })

function pairs(obj?: Record<string, unknown> | null): [string, string][] {
  if (!obj) return []
  return Object.entries(obj).map(([k, v]) => [LABELS[k] ?? k, v == null ? '-' : String(v)])
}

const sections = computed(() => [
  { title: '服务器信息', rows: pairs(server.value.sys) },
  { title: 'CPU', rows: pairs(server.value.cpu) },
  { title: '内存', rows: pairs(server.value.memory) },
  { title: 'JVM', rows: pairs(server.value.jvm) },
])
const diskRows = computed<DiskRow[]>(() => server.value.disk ?? [])
const redisRows = computed(() => pairs(redis.value))

async function load(): Promise<void> {
  loading.value = true
  try {
    server.value = await http.get<ServerInfo>('/system/monitor/server')
    redis.value = await http.get<Record<string, unknown>>('/system/monitor/redis')
  } catch (e) {
    snack.show = true
    snack.text = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void load()
})
</script>
