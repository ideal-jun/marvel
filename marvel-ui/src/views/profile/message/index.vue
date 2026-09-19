<template>
  <!-- 我的消息：本人可见的全部站内消息（服务端分页），
       支持标题/类型筛选、只看未读、全部已读、单条/批量删除；查看详情即标记已读 -->
  <div class="h-full flex flex-col gap-4">
    <SearchPanel @search="onSearch" @reset="onReset">
      <v-col cols="12" sm="6" md="3">
        <v-text-field
          v-model="query.title"
          label="标题"
          density="compact"
          hide-details
          clearable
          @keyup.enter="onSearch"
        />
      </v-col>
      <v-col cols="12" sm="6" md="3">
        <v-select
          v-model="query.type"
          :items="typeOptions"
          label="类型"
          density="compact"
          hide-details
          clearable
        />
      </v-col>
      <v-col cols="12" sm="6" md="3">
        <v-checkbox
          v-model="query.onlyUnread"
          label="只看未读"
          color="primary"
          density="compact"
          hide-details
        />
      </v-col>
    </SearchPanel>

    <ListPanel title="我的消息">
      <template #actions>
        <v-btn
          color="primary"
          prepend-icon="mdi-check-all"
          rounded="lg"
          :disabled="!notice.unread"
          @click="onReadAll"
        >
          全部已读
        </v-btn>
        <v-btn
          color="error"
          variant="tonal"
          prepend-icon="mdi-delete-outline"
          rounded="lg"
          :disabled="!selected.length"
          @click="askDelete(selected)"
        >
          批量删除
        </v-btn>
      </template>

      <v-data-table-server
        v-model="selected"
        class="flex-1 min-h-0"
        fixed-header
        show-select
        :headers="headers"
        :items="rows"
        :items-length="total"
        :items-per-page="query.pageSize"
        :page="query.pageNum"
        :loading="loading"
        item-value="noticeId"
        hover
        @update:options="onOptions"
      >
        <template #item.title="{ item }">
          <span :class="item.read ? 'text-medium-emphasis' : 'font-weight-bold'">{{
            item.title
          }}</span>
          <v-chip v-if="!item.read" color="error" size="x-small" class="ml-2">未读</v-chip>
        </template>
        <template #item.type="{ item }">
          <v-chip :color="item.type === '1' ? 'primary' : 'warning'" size="small" label>
            {{ NOTICE_TYPE_TEXT[item.type] ?? item.type }}
          </v-chip>
        </template>
        <template #item.actions="{ item }">
          <v-btn
            size="small"
            variant="text"
            prepend-icon="mdi-eye-outline"
            rounded="lg"
            @click="openDetail(item)"
          >
            查看
          </v-btn>
          <v-btn
            size="small"
            variant="text"
            color="error"
            icon="mdi-delete-outline"
            rounded="lg"
            @click="askDelete([item.noticeId])"
          />
        </template>
        <template #no-data>
          <div class="text-medium-emphasis py-8">
            {{ query.onlyUnread ? '没有未读消息' : '暂无消息' }}
          </div>
        </template>
      </v-data-table-server>
    </ListPanel>

    <v-dialog v-model="detailOpen" width="640">
      <v-card v-if="current" rounded="xl">
        <v-card-title class="flex items-center">
          <span>{{ current.title }}</span>
          <v-chip
            :color="current.type === '1' ? 'primary' : 'warning'"
            size="small"
            label
            class="ml-3"
          >
            {{ NOTICE_TYPE_TEXT[current.type] ?? current.type }}
          </v-chip>
        </v-card-title>
        <v-card-subtitle class="pb-0">{{ current.createTime }}</v-card-subtitle>
        <v-card-text class="text-body-1 text-wrap detail-content">{{
          current.content || '（无内容）'
        }}</v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn @click="detailOpen = false">关闭</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-dialog v-model="confirm.open" width="420">
      <v-card rounded="xl">
        <v-card-title>确认删除</v-card-title>
        <v-card-text
          >确定删除选中的
          {{ confirm.ids.length }} 条消息？删除后仅本人不可见，公告本身不受影响。</v-card-text
        >
        <v-card-actions>
          <v-spacer />
          <v-btn @click="confirm.open = false">取消</v-btn>
          <v-btn color="error" variant="tonal" :loading="deleting" @click="doDelete">删除</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-snackbar v-model="snack.show" :color="snack.color" timeout="3000">{{
      snack.text
    }}</v-snackbar>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import SearchPanel from '@/components/SearchPanel.vue'
import ListPanel from '@/components/ListPanel.vue'
import { http } from '@/api/request'
import { useNoticeStore } from '@/stores/notice'
import type { MyNoticeRow, PageResult } from '@/types/api'

const NOTICE_TYPE_TEXT: Record<string, string> = { '1': '通知', '2': '公告' }

const typeOptions = [
  { title: '通知', value: '1' },
  { title: '公告', value: '2' },
]

const notice = useNoticeStore()
const rows = ref<MyNoticeRow[]>([])
const total = ref(0)
const loading = ref(false)
const deleting = ref(false)
const selected = ref<number[]>([])
const detailOpen = ref(false)
const current = ref<MyNoticeRow | null>(null)
const snack = reactive({ show: false, text: '', color: 'success' })
const confirm = reactive<{ open: boolean; ids: number[] }>({ open: false, ids: [] })

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  onlyUnread: false,
  title: null as string | null,
  type: null as string | null,
})

const headers = [
  { title: '标题', key: 'title' },
  { title: '类型', key: 'type', width: 100 },
  { title: '时间', key: 'createTime', width: 200 },
  { title: '操作', key: 'actions', sortable: false, width: 140 },
].map((h) => ({ nowrap: true, ...h }))

function notify(text: string, color: 'success' | 'error' = 'success'): void {
  Object.assign(snack, { show: true, text, color })
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const page = await http.get<PageResult<MyNoticeRow>>('/system/notice/my', {
      params: {
        pageNum: query.pageNum,
        pageSize: query.pageSize,
        onlyUnread: query.onlyUnread,
        title: query.title,
        type: query.type,
      },
    })
    rows.value = page.records
    total.value = page.total
    // 删除后当前页可能已空，回退一页避免停在空白页
    if (!page.records.length && query.pageNum > 1) {
      query.pageNum -= 1
      await load()
    }
  } catch (e) {
    notify(e instanceof Error ? e.message : '加载失败', 'error')
  } finally {
    loading.value = false
  }
}

/** 筛选条件变化回到第一页，避免停留在越界页码 */
function onSearch(): void {
  query.pageNum = 1
  selected.value = []
  void load()
}

function onReset(): void {
  query.title = null
  query.type = null
  query.onlyUnread = false
  onSearch()
}

// 「只看未读」用 watch 而非 @update:model-value：后者与 v-model 绑定同一事件、
// 执行早于 v-model 赋值，load() 会读到旧值导致筛选不生效（复选框切换即时生效，无需点搜索）
watch(
  () => query.onlyUnread,
  () => onSearch(),
)

function onOptions(opts: { page: number; itemsPerPage: number }): void {
  query.pageNum = opts.page
  query.pageSize = opts.itemsPerPage
  void load()
}

/** 打开详情即标记已读（未读时），角标由 store 统一同步 */
async function openDetail(item: MyNoticeRow): Promise<void> {
  current.value = item
  detailOpen.value = true
  if (item.read) return
  try {
    await notice.markRead(item.noticeId)
    item.read = true
    // 只看未读模式下该条已不再匹配，移除后可继续看下一条
    if (query.onlyUnread) await load()
  } catch {
    notify('标记已读失败', 'error')
  }
}

async function onReadAll(): Promise<void> {
  try {
    await notice.markAllRead()
    notify('已全部标记为已读')
    await load()
  } catch (e) {
    notify(e instanceof Error ? e.message : '操作失败', 'error')
  }
}

function askDelete(ids: number[]): void {
  if (!ids.length) return
  confirm.ids = [...ids]
  confirm.open = true
}

async function doDelete(): Promise<void> {
  deleting.value = true
  try {
    await http.delete<null>(`/system/notice/my/${confirm.ids.join(',')}`)
    notify('已删除')
    confirm.open = false
    selected.value = []
    await load()
    // 删除未读消息会改变未读数，向服务端重新对齐角标
    await notice.refreshUnread()
  } catch (e) {
    notify(e instanceof Error ? e.message : '删除失败', 'error')
  } finally {
    deleting.value = false
  }
}

onMounted(() => {
  void load()
  void notice.refreshUnread()
})
</script>

<style scoped>
/* 消息正文保留换行，长内容在弹窗内滚动 */
.detail-content {
  max-height: 55vh;
  overflow-y: auto;
  white-space: pre-wrap;
}
</style>
