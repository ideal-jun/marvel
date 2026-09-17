<template>
  <!-- 满高两段式布局：上=搜索条件（可折叠），下=列表（占满剩余高度，表格内部滚动） -->
  <div class="h-full flex flex-col gap-4">
    <SearchPanel @search="load" @reset="onReset">
      <v-col cols="12" sm="6" md="3">
        <v-text-field
          v-model="query.title"
          label="标题"
          density="compact"
          hide-details
          clearable
          @keyup.enter="load"
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
    </SearchPanel>

    <ListPanel title="公告列表">
      <template #actions>
        <v-btn
          v-if="auth.hasPerm('system:notice:add')"
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
        item-value="noticeId"
        :loading="loading"
        hover
      >
        <template #item.type="{ item }">
          <v-chip :color="item.type === '1' ? 'primary' : 'warning'" size="small" label>
            {{ NOTICE_TYPE_TEXT[item.type] ?? item.type }}
          </v-chip>
        </template>
        <template #item.status="{ item }">
          <v-chip :color="item.status === '0' ? 'success' : 'grey'" size="small" label>
            {{ item.status === '0' ? '正常' : '关闭' }}
          </v-chip>
        </template>
        <template #item.actions="{ item }">
          <v-tooltip v-if="auth.hasPerm('system:notice:edit')" text="编辑">
            <template #activator="{ props: p }">
              <v-icon v-bind="p" icon="mdi-pencil" size="18" class="mr-3 text-secondary" @click="openEdit(item)" />
            </template>
          </v-tooltip>
          <v-tooltip v-if="auth.hasPerm('system:notice:remove')" text="删除">
            <template #activator="{ props: p }">
              <v-icon v-bind="p" icon="mdi-delete" size="18" class="text-error" @click="onDelete(item)" />
            </template>
          </v-tooltip>
        </template>
      </v-data-table>
    </ListPanel>

    <v-dialog v-model="dialog" width="640">
      <v-card :title="form.noticeId ? '修改公告' : '新增公告'" rounded="xl">
        <v-card-text>
          <v-text-field v-model="form.title" label="公告标题" />
          <v-radio-group v-model="form.type" inline label="类型">
            <v-radio label="通知" value="1" />
            <v-radio label="公告" value="2" />
          </v-radio-group>
          <v-textarea v-model="form.content" label="公告内容" rows="6" />
          <v-radio-group v-model="form.status" inline label="状态">
            <v-radio label="正常" value="0" />
            <v-radio label="关闭" value="1" />
          </v-radio-group>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn @click="dialog = false">取消</v-btn>
          <v-btn color="primary" @click="onSave">保存</v-btn>
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
import type { SysNoticeRow } from '@/types/api'

const NOTICE_TYPE_TEXT: Record<string, string> = { '1': '通知', '2': '公告' }

const auth = useAuthStore()
const rows = ref<SysNoticeRow[]>([])
const loading = ref(false)
const dialog = ref(false)
const typeOptions = [
  { title: '通知', value: '1' },
  { title: '公告', value: '2' },
]
const query = reactive({ title: '' as string | null, type: null })
const form = reactive<Partial<SysNoticeRow>>({})
const snack = reactive({ show: false, text: '', color: 'success' })

const headers = [
  { title: 'ID', key: 'noticeId', width: 70 },
  { title: '标题', key: 'title' },
  { title: '类型', key: 'type', width: 90 },
  { title: '状态', key: 'status', width: 90 },
  { title: '创建时间', key: 'createTime', width: 180 },
  { title: '操作', key: 'actions', fixed: 'end' as const, width: 110, sortable: false },
].map((h) => ({ nowrap: true, ...h }))

function notify(text: string, color: 'success' | 'error' = 'success'): void {
  Object.assign(snack, { show: true, text, color })
}

async function load(): Promise<void> {
  loading.value = true
  try {
    rows.value = await http.get<SysNoticeRow[]>('/system/notice/list', { params: { ...query } })
  } catch (e) {
    notify(e instanceof Error ? e.message : '加载失败', 'error')
  } finally {
    loading.value = false
  }
}

function onReset(): void {
  query.title = null
  query.type = null
  void load()
}

function openAdd(): void {
  clearObject(form)
  Object.assign(form, { type: '1', status: '0' })
  dialog.value = true
}

function openEdit(item: SysNoticeRow): void {
  clearObject(form)
  Object.assign(form, item)
  dialog.value = true
}

async function onSave(): Promise<void> {
  try {
    if (form.noticeId) {
      await http.put<null>('/system/notice', form)
    } else {
      await http.post<null>('/system/notice', form)
    }
    dialog.value = false
    notify('保存成功')
    void load()
  } catch (e) {
    notify(e instanceof Error ? e.message : '保存失败', 'error')
  }
}

async function onDelete(item: SysNoticeRow): Promise<void> {
  if (!window.confirm(`确认删除公告「${item.title}」？`)) return
  try {
    await http.delete<null>(`/system/notice/${item.noticeId}`)
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
