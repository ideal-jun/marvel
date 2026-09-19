<template>
  <div class="h-full flex flex-col gap-4">
    <SearchPanel @search="onSearch" @reset="onReset">
      <v-col cols="12" sm="6" md="4">
        <v-text-field
          v-model="query.fileName"
          label="文件名"
          density="compact"
          hide-details
          clearable
          @keyup.enter="onSearch"
        />
      </v-col>
    </SearchPanel>

    <ListPanel title="文件列表">
      <template #actions>
        <v-btn
          v-if="auth.hasPerm('infra:file:upload')"
          color="success"
          prepend-icon="mdi-upload"
          rounded="lg"
          @click="pickFile"
        >
          上传
        </v-btn>
        <input ref="fileInput" type="file" style="display: none" @change="onUpload" />
        <v-btn
          v-if="auth.hasPerm('infra:file:remove')"
          color="error"
          variant="tonal"
          prepend-icon="mdi-delete-outline"
          rounded="lg"
          :disabled="!selected.length"
          @click="onBatchDelete"
        >
          批量删除{{ selected.length ? `(${selected.length})` : '' }}
        </v-btn>
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

      <v-data-table-server
        v-model="selected"
        class="flex-1 min-h-0"
        fixed-header
        :headers="headers"
        :items="rows"
        :items-length="total"
        :items-per-page="query.pageSize"
        :page="query.pageNum"
        :loading="loading"
        item-value="fileId"
        show-select
        hover
        @update:options="onOptions"
      >
        <template #item.fileSize="{ item }">{{ human(item.fileSize) }}</template>
        <template #item.actions="{ item }">
          <v-tooltip v-if="auth.hasPerm('infra:file:download')" text="下载">
            <template #activator="{ props: p }">
              <v-icon
                v-bind="p"
                icon="mdi-download"
                size="18"
                class="mr-3 text-primary"
                @click="onDownload(item)"
              />
            </template>
          </v-tooltip>
          <v-tooltip v-if="auth.hasPerm('infra:file:remove')" text="删除">
            <template #activator="{ props: p }">
              <v-icon
                v-bind="p"
                icon="mdi-delete"
                size="18"
                class="text-error"
                @click="onDelete(item)"
              />
            </template>
          </v-tooltip>
        </template>
      </v-data-table-server>
    </ListPanel>

    <v-snackbar v-model="snack.show" :color="snack.color" timeout="3000">{{
      snack.text
    }}</v-snackbar>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import SearchPanel from '@/components/SearchPanel.vue'
import ListPanel from '@/components/ListPanel.vue'
import { useAuthStore } from '@/stores/auth'
import instance, { http } from '@/api/request'
import type { PageResult } from '@/types/api'

interface SysFileRow {
  fileId: number
  fileName: string
  filePath: string
  fileSize: number
  contentType: string
  createTime: string
}

const auth = useAuthStore()
const rows = ref<SysFileRow[]>([])
const total = ref(0)
const loading = ref(false)
const selected = ref<number[]>([])
const fileInput = ref<HTMLInputElement | null>(null)
const query = reactive({ pageNum: 1, pageSize: 10, fileName: null as string | null })
const snack = reactive({ show: false, text: '', color: 'success' })

const headers = [
  { title: 'ID', key: 'fileId', width: 70, nowrap: true },
  { title: '文件名', key: 'fileName', nowrap: true },
  { title: '类型', key: 'contentType', width: 180, nowrap: true },
  { title: '大小', key: 'fileSize', width: 120, nowrap: true },
  { title: '上传时间', key: 'createTime', width: 180, nowrap: true },
  {
    title: '操作',
    key: 'actions',
    fixed: 'end' as const,
    width: 110,
    sortable: false,
    nowrap: true,
  },
]

function notify(text: string, color: 'success' | 'error' = 'success'): void {
  Object.assign(snack, { show: true, text, color })
}

function human(bytes: number): string {
  if (bytes === null || bytes === undefined || Number.isNaN(bytes)) return '-'
  if (bytes < 1024) return bytes + ' B'
  const units = ['KB', 'MB', 'GB', 'TB']
  let value = bytes
  let i = -1
  while (value >= 1024 && i < units.length - 1) {
    value /= 1024
    i++
  }
  return value.toFixed(2) + ' ' + units[i]
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const page = await http.get<PageResult<SysFileRow>>('/infra/file/page', {
      params: { ...query },
    })
    rows.value = page.records
    total.value = page.total
  } catch (e) {
    notify(e instanceof Error ? e.message : '加载失败', 'error')
  } finally {
    loading.value = false
  }
}

function onSearch(): void {
  query.pageNum = 1
  void load()
}

function onReset(): void {
  query.fileName = null
  onSearch()
}

function onOptions(opts: { page: number; itemsPerPage: number }): void {
  query.pageNum = opts.page
  query.pageSize = opts.itemsPerPage
  void load()
}

function pickFile(): void {
  fileInput.value?.click()
}

async function onUpload(e: Event): Promise<void> {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  try {
    const fd = new FormData()
    fd.append('file', file)
    await http.post<{ url: string }>('/infra/file/upload', fd)
    notify('上传成功')
    void load()
  } catch (err) {
    notify(err instanceof Error ? err.message : '上传失败', 'error')
  } finally {
    input.value = ''
  }
}

async function onDownload(item: SysFileRow): Promise<void> {
  try {
    const resp = await instance.get('/infra/file/download/' + item.fileId, { responseType: 'blob' })
    const url = URL.createObjectURL(resp.data as Blob)
    const a = document.createElement('a')
    a.href = url
    a.download = item.fileName || 'download'
    document.body.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(url)
  } catch (e) {
    notify(e instanceof Error ? e.message : '下载失败', 'error')
  }
}

async function onDelete(item: SysFileRow): Promise<void> {
  if (!window.confirm('确认删除文件「' + item.fileName + '」？')) return
  try {
    await http.delete<null>('/infra/file/' + item.fileId)
    notify('删除成功')
    void load()
  } catch (e) {
    notify(e instanceof Error ? e.message : '删除失败', 'error')
  }
}

async function onBatchDelete(): Promise<void> {
  if (!selected.value.length) return
  if (!window.confirm('确认删除选中的 ' + selected.value.length + ' 个文件？')) return
  try {
    await http.delete<null>('/infra/file/' + selected.value.join(','))
    selected.value = []
    notify('删除成功')
    void load()
  } catch (e) {
    notify(e instanceof Error ? e.message : '删除失败', 'error')
  }
}

onMounted(() => {
  void load()
})
</script>
