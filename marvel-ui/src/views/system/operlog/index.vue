<template>
  <!-- 满高两段式布局：上=搜索条件（可折叠），下=日志列表（分页，表格内部滚动） -->
  <div class="h-full flex flex-col gap-4">
    <SearchPanel @search="onSearch" @reset="onReset">
      <v-col cols="12" sm="6" md="3">
        <v-text-field
          v-model="query.title"
          label="模块标题"
          density="compact"
          hide-details
          clearable
          @keyup.enter="onSearch"
        />
      </v-col>
      <v-col cols="12" sm="6" md="3">
        <v-text-field
          v-model="query.operUser"
          label="操作人"
          density="compact"
          hide-details
          clearable
          @keyup.enter="onSearch"
        />
      </v-col>
      <v-col cols="12" sm="6" md="3">
        <v-select
          v-model="query.status"
          :items="STATUS_OPTIONS"
          label="状态"
          density="compact"
          hide-details
          clearable
        />
      </v-col>
      <v-col cols="12" sm="6" md="3">
        <v-select
          v-model="query.businessType"
          :items="BIZ_OPTIONS"
          label="操作类型"
          density="compact"
          hide-details
          clearable
        />
      </v-col>
      <v-col cols="12" sm="6" md="3">
        <v-date-input
          :model-value="beginDate"
          label="开始日期"
          density="compact"
          hide-details
          clearable
          prepend-icon=""
          prepend-inner-icon="$calendar"
          @update:model-value="beginDate = $event"
        />
      </v-col>
      <v-col cols="12" sm="6" md="3">
        <v-date-input
          :model-value="endDate"
          label="结束日期"
          density="compact"
          hide-details
          clearable
          prepend-icon=""
          prepend-inner-icon="$calendar"
          @update:model-value="endDate = $event"
        />
      </v-col>
    </SearchPanel>

    <ListPanel title="操作日志">
      <template #actions>
        <v-btn
          v-if="auth.hasPerm('system:operlog:remove')"
          color="error"
          variant="tonal"
          prepend-icon="mdi-delete-outline"
          rounded="lg"
          :disabled="!selected.length"
          @click="onBatchDelete"
        >
          批量删除{{ selected.length ? `(${selected.length})` : '' }}
        </v-btn>
        <v-btn
          v-if="auth.hasPerm('system:operlog:clean')"
          color="warning"
          variant="tonal"
          prepend-icon="mdi-broom"
          rounded="lg"
          @click="onClean"
        >
          清空
        </v-btn>
        <v-tooltip text="刷新" location="bottom">
          <template #activator="{ props }">
            <v-btn v-bind="props" icon="mdi-refresh" variant="text" rounded="lg" :loading="loading" @click="load" />
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
        item-value="operId"
        show-select
        hover
        @update:options="onOptions"
      >
        <template #item.status="{ item }">
          <v-chip :color="item.status === '0' ? 'success' : 'error'" size="small" label>
            {{ item.status === '0' ? '成功' : '失败' }}
          </v-chip>
        </template>
        <template #item.method="{ item }">
          <v-tooltip :text="item.method" location="top">
            <template #activator="{ props: p }">
              <span v-bind="p" class="text-body-2 text-secondary truncate inline-block max-w-40 align-middle cursor-help">{{ item.method }}</span>
            </template>
          </v-tooltip>
        </template>
        <template #item.operParam="{ item }">
          <v-tooltip v-if="item.operParam" :text="item.operParam" location="top">
            <template #activator="{ props: p }">
              <span v-bind="p" class="text-caption text-secondary truncate inline-block max-w-44 align-middle cursor-help">参数</span>
            </template>
          </v-tooltip>
          <span v-else class="text-disabled text-caption">-</span>
        </template>
        <template #item.errorMsg="{ item }">
          <v-tooltip v-if="item.errorMsg" :text="item.errorMsg" location="top">
            <template #activator="{ props: p }">
              <span v-bind="p" class="text-caption text-error truncate inline-block max-w-44 align-middle cursor-help">{{ item.errorMsg }}</span>
            </template>
          </v-tooltip>
          <span v-else class="text-disabled text-caption">-</span>
        </template>
      </v-data-table-server>
    </ListPanel>

    <v-snackbar v-model="snack.show" :color="snack.color" timeout="3000">{{ snack.text }}</v-snackbar>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import SearchPanel from '@/components/SearchPanel.vue'
import ListPanel from '@/components/ListPanel.vue'
import { useAuthStore } from '@/stores/auth'
import { http } from '@/api/request'
import type { PageResult, SysOperLogRow } from '@/types/api'

const STATUS_OPTIONS = [
  { title: '成功', value: '0' },
  { title: '失败', value: '1' },
]
const BIZ_OPTIONS = [
  { title: '新增', value: 'INSERT' },
  { title: '修改', value: 'UPDATE' },
  { title: '删除', value: 'DELETE' },
  { title: '清空', value: 'CLEAN' },
  { title: '其他', value: 'OTHER' },
]

const auth = useAuthStore()
const rows = ref<SysOperLogRow[]>([])
const total = ref(0)
const loading = ref(false)
const selected = ref<number[]>([])
const beginDate = ref<Date | null>(null)
const endDate = ref<Date | null>(null)

interface LogQuery {
  pageNum: number
  pageSize: number
  title: string | null
  operUser: string | null
  status: string | null
  /** 操作类型（INSERT/UPDATE/...），请求时按 method 前缀过滤 */
  businessType: string | null
  beginTime: string | undefined
  endTime: string | undefined
}
const query = reactive<LogQuery>({
  pageNum: 1,
  pageSize: 10,
  title: null,
  operUser: null,
  status: null,
  businessType: null,
  beginTime: undefined,
  endTime: undefined,
})

const snack = reactive({ show: false, text: '', color: 'success' })

const headers = [
  { title: 'ID', key: 'operId', width: 70 },
  { title: '模块', key: 'title', width: 110 },
  { title: '方法', key: 'method', width: 200 },
  { title: '请求方式', key: 'requestMethod', width: 90 },
  { title: '操作人', key: 'operUser', width: 100 },
  { title: 'IP', key: 'operIp', width: 120 },
  { title: '参数', key: 'operParam', width: 70 },
  { title: '状态', key: 'status', width: 80 },
  { title: '错误消息', key: 'errorMsg', width: 180 },
  { title: '操作时间', key: 'operTime', width: 170 },
]

function notify(text: string, color: 'success' | 'error' = 'success'): void {
  Object.assign(snack, { show: true, text, color })
}

/** 日期选择值为当天起止语义：开始取 00:00:00，结束取 23:59:59 */
function fmtLocal(d: Date, endOfDay = false): string {
  const p = (n: number): string => String(n).padStart(2, '0')
  const hms = endOfDay ? '23:59:59' : '00:00:00'
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${hms}`
}

async function load(): Promise<void> {
  loading.value = true
  try {
    query.beginTime = beginDate.value ? fmtLocal(beginDate.value) : undefined
    query.endTime = endDate.value ? fmtLocal(endDate.value, true) : undefined
    const page = await http.get<PageResult<SysOperLogRow>>('/system/operlog/page', { params: { ...query } })
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
  query.title = null
  query.operUser = null
  query.status = null
  query.businessType = null
  beginDate.value = null
  endDate.value = null
  onSearch()
}

function onOptions(opts: { page: number; itemsPerPage: number }): void {
  query.pageNum = opts.page
  query.pageSize = opts.itemsPerPage
  void load()
}

async function onBatchDelete(): Promise<void> {
  if (!selected.value.length) return
  if (!window.confirm(`确认删除选中的 ${selected.value.length} 条日志？`)) return
  try {
    await http.delete<null>(`/system/operlog/${selected.value.join(',')}`)
    selected.value = []
    notify('删除成功')
    void load()
  } catch (e) {
    notify(e instanceof Error ? e.message : '删除失败', 'error')
  }
}

async function onClean(): Promise<void> {
  if (!window.confirm('确认清空全部操作日志？该操作不可恢复！')) return
  try {
    await http.delete<null>('/system/operlog/clean')
    selected.value = []
    notify('已清空')
    void load()
  } catch (e) {
    notify(e instanceof Error ? e.message : '操作失败', 'error')
  }
}

onMounted(() => {
  void load()
})
</script>
