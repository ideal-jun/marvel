<template>
  <v-tooltip text="站内消息" location="bottom">
    <template #activator="{ props }">
      <v-btn v-bind="props" variant="text" rounded="lg" @click="open">
        <v-badge :content="unread" :model-value="unread > 0" color="error" floating>
          <v-icon icon="mdi-bell-outline" />
        </v-badge>
      </v-btn>
    </template>
  </v-tooltip>

  <v-dialog v-model="dialog" width="680">
    <v-card title="站内消息" rounded="xl">
      <v-card-text class="pa-0" style="max-height: 60vh; overflow: auto">
        <v-list v-if="items.length" lines="three">
          <v-list-item
            v-for="item in items"
            :key="item.noticeId"
            class="cursor-pointer"
            @click="onRead(item)"
          >
            <template #prepend>
              <v-icon
                :color="item.read ? 'grey' : 'primary'"
                :icon="item.read ? 'mdi-email-open-outline' : 'mdi-email-outline'"
              />
            </template>
            <v-list-item-title :class="item.read ? 'text-medium-emphasis' : 'font-weight-bold'">
              {{ item.title }}
              <v-chip v-if="!item.read" color="error" size="x-small" class="ml-2">未读</v-chip>
            </v-list-item-title>
            <v-list-item-subtitle class="text-wrap">{{ item.content }}</v-list-item-subtitle>
            <template #append>
              <span class="text-caption text-medium-emphasis ml-2">{{ item.createTime }}</span>
            </template>
          </v-list-item>
        </v-list>
        <div v-else class="text-center text-medium-emphasis py-8">
          {{ loading ? '加载中…' : '暂无消息' }}
        </div>
      </v-card-text>
      <v-card-actions>
        <v-btn variant="text" :disabled="!unread" @click="onReadAll">全部已读</v-btn>
        <v-spacer />
        <v-btn @click="dialog = false">关闭</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { http } from '@/api/request'
import type { PageResult } from '@/types/api'

interface NoticeMessage {
  noticeId: number
  title: string
  content: string | null
  type: string
  createTime: string | null
  read: boolean
}

const dialog = ref(false)
const loading = ref(false)
const unread = ref(0)
const items = ref<NoticeMessage[]>([])
let timer: number | undefined

async function loadUnread(): Promise<void> {
  try {
    unread.value = await http.get<number>('/system/notice/unread-count')
  } catch {
    // 轮询失败不打扰用户
  }
}

async function loadList(): Promise<void> {
  loading.value = true
  try {
    const page = await http.get<PageResult<NoticeMessage>>('/system/notice/my', {
      params: { pageNum: 1, pageSize: 20 },
    })
    items.value = page.records
  } catch {
    // 打开失败保持原列表
  } finally {
    loading.value = false
  }
}

async function open(): Promise<void> {
  dialog.value = true
  await loadList()
}

async function onRead(item: NoticeMessage): Promise<void> {
  if (item.read) return
  try {
    await http.post<null>('/system/notice/read/' + item.noticeId)
    item.read = true
    await loadUnread()
  } catch {
    // 忽略单条失败
  }
}

async function onReadAll(): Promise<void> {
  try {
    await http.post<null>('/system/notice/read-all')
    await Promise.all([loadList(), loadUnread()])
  } catch {
    // 忽略
  }
}

onMounted(() => {
  void loadUnread()
  timer = window.setInterval(() => void loadUnread(), 60000)
})

onUnmounted(() => {
  if (timer) {
    window.clearInterval(timer)
  }
})
</script>
