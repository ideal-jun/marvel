<template>
  <!-- 在线用户：Sa-Token 活跃会话列表（按用户去重），支持强制下线 -->
  <div class="h-full flex flex-col gap-4">
    <ListPanel title="在线用户">
      <template #actions>
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
      </template>

      <v-data-table
        class="flex-1 min-h-0"
        fixed-header
        :headers="headers"
        :items="rows"
        :loading="loading"
        hover
        items-per-page="10"
      >
        <template #item.loginTime="{ item }">
          {{ fmtTime(item.loginTime) }}
        </template>
        <template #item.tokenTimeout="{ item }">
          <v-chip size="small" label :color="item.tokenTimeout > 0 ? 'primary' : 'error'">
            {{ fmtTimeout(item.tokenTimeout) }}
          </v-chip>
        </template>
        <template #item.actions="{ item }">
          <v-tooltip text="强制下线" location="bottom">
            <template #activator="{ props }">
              <v-icon
                v-if="auth.hasPerm('system:online:kickout')"
                v-bind="props"
                icon="mdi-logout"
                color="error"
                size="small"
                class="cursor-pointer"
                @click="onKickout(item)"
              />
            </template>
          </v-tooltip>
        </template>
      </v-data-table>
    </ListPanel>

    <v-snackbar v-model="snack.show" :color="snack.color" timeout="3000">{{
      snack.text
    }}</v-snackbar>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import ListPanel from '@/components/ListPanel.vue'
import { useAuthStore } from '@/stores/auth'
import { http } from '@/api/request'
import type { OnlineRow } from '@/types/api'

const auth = useAuthStore()
const rows = ref<OnlineRow[]>([])
const loading = ref(false)

const snack = reactive({ show: false, text: '', color: 'success' })

const headers = [
  { title: '用户 ID', key: 'userId', width: 90 },
  { title: '登录账号', key: 'username', width: 140 },
  { title: '登录 IP', key: 'loginIp', width: 140 },
  { title: '浏览器 / 系统', key: 'userAgent' },
  { title: '登录时间', key: 'loginTime', width: 170 },
  { title: '会话剩余', key: 'tokenTimeout', width: 120 },
  { title: '操作', key: 'actions', fixed: 'end' as const, width: 70, sortable: false },
].map((h) => ({ nowrap: true, ...h }))

function notify(text: string, color: 'success' | 'error' = 'success'): void {
  Object.assign(snack, { show: true, text, color })
}

function fmtTime(epochMillis: number | null): string {
  if (!epochMillis) return '-'
  const d = new Date(epochMillis)
  const p = (n: number): string => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

function fmtTimeout(seconds: number): string {
  if (seconds < 0) return '已过期'
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  if (h > 0) return `${h} 小时 ${m} 分`
  if (m > 0) return `${m} 分 ${seconds % 60} 秒`
  return `${seconds} 秒`
}

async function load(): Promise<void> {
  loading.value = true
  try {
    rows.value = await http.get<OnlineRow[]>('/system/online/list')
  } catch (e) {
    notify(e instanceof Error ? e.message : '加载失败', 'error')
  } finally {
    loading.value = false
  }
}

async function onKickout(row: OnlineRow): Promise<void> {
  if (
    !window.confirm(`确认将用户「${row.username ?? row.userId}」强制下线？其全部活跃会话将被注销。`)
  )
    return
  try {
    await http.delete<null>(`/system/online/${row.userId}`)
    notify('已强制下线')
    void load()
  } catch (e) {
    notify(e instanceof Error ? e.message : '操作失败', 'error')
  }
}

onMounted(() => {
  void load()
})
</script>
