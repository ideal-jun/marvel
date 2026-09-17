<template>
  <!-- 满高两段式布局：上=搜索条件（可折叠），下=列表（占满剩余高度，表格内部滚动） -->
  <div class="h-full flex flex-col gap-4">
    <SearchPanel @search="load" @reset="onReset">
      <v-col cols="12" sm="6" md="3">
        <v-text-field
          v-model="query.jobName"
          label="任务名称"
          density="compact"
          hide-details
          clearable
          @keyup.enter="load"
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
    </SearchPanel>

    <ListPanel title="任务列表">
      <template #actions>
        <v-btn
          v-if="auth.hasPerm('infra:job:add')"
          color="success"
          prepend-icon="mdi-plus"
          rounded="lg"
          @click="openAdd"
        >
          新增
        </v-btn>
      </template>

      <v-data-table
        class="flex-1 min-h-0"
        fixed-header
        :headers="headers"
        :items="rows"
        item-value="jobId"
        :loading="loading"
        hover
      >
        <template #item.status="{ item }">
          <v-chip :color="item.status === '0' ? 'success' : 'grey'" size="small" label>
            {{ item.status === '0' ? '运行中' : '已暂停' }}
          </v-chip>
        </template>
        <template #item.actions="{ item }">
          <v-tooltip v-if="auth.hasPerm('infra:job:run')" text="立即执行">
            <template #activator="{ props: p }">
              <v-icon v-bind="p" icon="mdi-play" size="18" class="mr-3 text-success" @click="onRun(item)" />
            </template>
          </v-tooltip>
          <v-tooltip v-if="auth.hasPerm('infra:job:list')" text="执行日志">
            <template #activator="{ props: p }">
              <v-icon v-bind="p" icon="mdi-text-box-outline" size="18" class="mr-3 text-secondary" @click="openLogs(item)" />
            </template>
          </v-tooltip>
          <v-tooltip v-if="auth.hasPerm('infra:job:edit')" :text="item.status === '0' ? '暂停' : '恢复'">
            <template #activator="{ props: p }">
              <v-icon
                v-bind="p"
                :icon="item.status === '0' ? 'mdi-pause' : 'mdi-play-outline'"
                size="18"
                class="mr-3 text-warning"
                @click="onToggleStatus(item)"
              />
            </template>
          </v-tooltip>
          <v-tooltip v-if="auth.hasPerm('infra:job:edit')" text="编辑">
            <template #activator="{ props: p }">
              <v-icon v-bind="p" icon="mdi-pencil" size="18" class="mr-3 text-secondary" @click="openEdit(item)" />
            </template>
          </v-tooltip>
          <v-tooltip v-if="auth.hasPerm('infra:job:remove')" text="删除">
            <template #activator="{ props: p }">
              <v-icon v-bind="p" icon="mdi-delete" size="18" class="text-error" @click="onDelete(item)" />
            </template>
          </v-tooltip>
        </template>
      </v-data-table>
    </ListPanel>

    <!-- 任务编辑对话框 -->
    <v-dialog v-model="dialog" width="560">
      <v-card :title="form.jobId ? '修改任务' : '新增任务'" rounded="xl">
        <v-card-text>
          <v-text-field v-model="form.jobName" label="任务名称" />
          <v-text-field v-model="form.invokeTarget" label="调用目标（如 sampleJob.run）" />
          <div class="flex items-center gap-2">
            <v-text-field v-model="form.cronExpression" label="cron 表达式（如 0 * * * * ?）" hide-details />
            <v-btn variant="tonal" rounded="lg" @click="previewCron">预览</v-btn>
          </div>
          <div
            v-if="cronPreview"
            class="text-caption mt-1 mb-2"
            :class="cronPreview.valid ? 'text-success' : 'text-error'"
          >
            <template v-if="cronPreview.valid">未来执行：{{ (cronPreview.nextTimes || []).join('、') }}</template>
            <template v-else>{{ cronPreview.message }}</template>
          </div>
          <v-radio-group v-model="form.status" inline label="状态">
            <v-radio label="运行中" value="0" />
            <v-radio label="暂停" value="1" />
          </v-radio-group>
          <v-text-field v-model="form.remark" label="备注" />
          <div class="text-caption text-medium-emphasis">
            调用目标为 Spring Bean 名 + 无参方法名；内置演示任务：sampleJob.run
          </div>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn @click="dialog = false">取消</v-btn>
          <v-btn color="primary" @click="onSave">保存</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- 执行日志对话框：服务端分页 + 失败重试 + 清空 -->
    <v-dialog v-model="logDialog" width="860">
      <v-card title="执行日志" rounded="xl">
        <v-card-text>
          <v-data-table-server
            :headers="logHeaders"
            :items="logs"
            :items-length="logTotal"
            :items-per-page="logQuery.pageSize"
            :page="logQuery.pageNum"
            :loading="logLoading"
            item-value="jobLogId"
            hover
            @update:options="onLogOptions"
          >
            <template #item.status="{ item }">
              <v-chip :color="item.status === '0' ? 'success' : 'error'" size="small" label>
                {{ item.status === '0' ? '成功' : '失败' }}
              </v-chip>
            </template>
            <template #item.endTime="{ item }">
              {{ item.endTime }}
              <div v-if="item.errorMsg" class="text-caption text-error">{{ item.errorMsg }}</div>
            </template>
            <template #item.actions="{ item }">
              <v-tooltip v-if="item.status === '1' && auth.hasPerm('infra:job:run')" text="重试">
                <template #activator="{ props: p }">
                  <v-icon v-bind="p" icon="mdi-refresh" size="18" class="text-primary" @click="onRetry(item)" />
                </template>
              </v-tooltip>
            </template>
          </v-data-table-server>
        </v-card-text>
        <v-card-actions>
          <v-btn
            v-if="auth.hasPerm('infra:job:remove')"
            color="error"
            variant="tonal"
            prepend-icon="mdi-delete-sweep-outline"
            rounded="lg"
            @click="onCleanLogs"
          >
            清空日志
          </v-btn>
          <v-spacer />
          <v-btn @click="logDialog = false">关闭</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-snackbar v-model="snack.show" :color="snack.color" timeout="3000">{{ snack.text }}</v-snackbar>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import SearchPanel from '@/components/SearchPanel.vue'
import ListPanel from '@/components/ListPanel.vue'
import { useAuthStore } from '@/stores/auth'
import { http } from '@/api/request'
import { clearObject } from '@/utils/object'
import type { PageResult, SysJobLogRow, SysJobRow } from '@/types/api'

const STATUS_OPTIONS = [
  { title: '运行中', value: '0' },
  { title: '已暂停', value: '1' },
]

const auth = useAuthStore()
const rows = ref<SysJobRow[]>([])
const loading = ref(false)
const dialog = ref(false)
const logDialog = ref(false)
const logLoading = ref(false)
const logs = ref<SysJobLogRow[]>([])
const logTotal = ref(0)
const currentLogJobId = ref<number | null>(null)
const logQuery = reactive({ pageNum: 1, pageSize: 10 })
interface CronPreview { valid: boolean; nextTimes?: string[]; message?: string }
const cronPreview = ref<CronPreview | null>(null)
const query = reactive({ jobName: '' as string | null, status: null })
const form = reactive<Partial<SysJobRow>>({})
const snack = reactive({ show: false, text: '', color: 'success' })

const headers = [
  { title: 'ID', key: 'jobId', width: 70 },
  { title: '任务名称', key: 'jobName' },
  { title: '调用目标', key: 'invokeTarget' },
  { title: 'cron 表达式', key: 'cronExpression' },
  { title: '状态', key: 'status', width: 90 },
  { title: '操作', key: 'actions', fixed: 'end' as const, width: 200, sortable: false },
].map((h) => ({ nowrap: true, ...h }))

const logHeaders = [
  { title: '日志ID', key: 'jobLogId', width: 80 },
  { title: '状态', key: 'status', width: 80 },
  { title: '开始时间', key: 'startTime', width: 180 },
  { title: '结束时间', key: 'endTime' },
  { title: '操作', key: 'actions', fixed: 'end' as const, width: 80, sortable: false },
].map((h) => ({ nowrap: true, ...h }))

function notify(text: string, color: 'success' | 'error' = 'success'): void {
  Object.assign(snack, { show: true, text, color })
}

async function load(): Promise<void> {
  loading.value = true
  try {
    rows.value = await http.get<SysJobRow[]>('/infra/job/list', { params: { ...query } })
  } catch (e) {
    notify(e instanceof Error ? e.message : '加载失败', 'error')
  } finally {
    loading.value = false
  }
}

function onReset(): void {
  query.jobName = null
  query.status = null
  void load()
}

function openAdd(): void {
  clearObject(form)
  Object.assign(form, { status: '1', jobGroup: 'DEFAULT' })
  cronPreview.value = null
  dialog.value = true
}

function openEdit(item: SysJobRow): void {
  clearObject(form)
  Object.assign(form, item)
  cronPreview.value = null
  dialog.value = true
}

async function onSave(): Promise<void> {
  try {
    if (form.jobId) {
      await http.put<null>('/infra/job', form)
    } else {
      await http.post<null>('/infra/job', form)
    }
    dialog.value = false
    cronPreview.value = null
    notify('保存成功')
    void load()
  } catch (e) {
    notify(e instanceof Error ? e.message : '保存失败', 'error')
  }
}

async function onRun(item: SysJobRow): Promise<void> {
  try {
    const cost = await http.put<number>(`/infra/job/run/${item.jobId}`)
    notify(`执行成功，耗时 ${cost}ms`)
    void load()
  } catch (e) {
    notify(e instanceof Error ? e.message : '执行失败', 'error')
  }
}

/** 运行中→暂停，暂停→恢复 */
async function onToggleStatus(item: SysJobRow): Promise<void> {
  const next = item.status === '0' ? '1' : '0'
  try {
    await http.put<null>(`/infra/job/changeStatus?jobId=${item.jobId}&status=${next}`)
    notify(next === '0' ? '已恢复' : '已暂停')
    void load()
  } catch (e) {
    notify(e instanceof Error ? e.message : '操作失败', 'error')
  }
}

async function onDelete(item: SysJobRow): Promise<void> {
  if (!window.confirm(`确认删除任务「${item.jobName}」？`)) return
  try {
    await http.delete<null>(`/infra/job/${item.jobId}`)
    notify('删除成功')
    void load()
  } catch (e) {
    notify(e instanceof Error ? e.message : '删除失败', 'error')
  }
}

async function previewCron(): Promise<void> {
  if (!form.cronExpression) {
    cronPreview.value = null
    return
  }
  try {
    cronPreview.value = await http.get<CronPreview>('/infra/job/validate-cron', {
      params: { cron: form.cronExpression },
    })
  } catch (e) {
    cronPreview.value = { valid: false, message: e instanceof Error ? e.message : '校验失败' }
  }
}

async function loadLogs(): Promise<void> {
  logLoading.value = true
  try {
    const page = await http.get<PageResult<SysJobLogRow>>('/infra/job/log/page', {
      params: { jobId: currentLogJobId.value, ...logQuery },
    })
    logs.value = page.records
    logTotal.value = page.total
  } catch (e) {
    notify(e instanceof Error ? e.message : '加载失败', 'error')
  } finally {
    logLoading.value = false
  }
}

function onLogOptions(opts: { page: number; itemsPerPage: number }): void {
  logQuery.pageNum = opts.page
  logQuery.pageSize = opts.itemsPerPage
  void loadLogs()
}

async function openLogs(item: SysJobRow): Promise<void> {
  currentLogJobId.value = item.jobId
  logQuery.pageNum = 1
  logDialog.value = true
  await loadLogs()
}

async function onRetry(item: SysJobLogRow): Promise<void> {
  try {
    const cost = await http.post<number>('/infra/job/retry/' + item.jobLogId)
    notify('重试成功，耗时 ' + cost + 'ms')
    void loadLogs()
    void load()
  } catch (e) {
    notify(e instanceof Error ? e.message : '重试失败', 'error')
  }
}

async function onCleanLogs(): Promise<void> {
  if (!window.confirm('确认清空全部执行日志？')) return
  try {
    await http.delete<null>('/infra/job/log/clean')
    notify('已清空')
    void loadLogs()
  } catch (e) {
    notify(e instanceof Error ? e.message : '清空失败', 'error')
  }
}

onMounted(() => {
  void load()
})
</script>
