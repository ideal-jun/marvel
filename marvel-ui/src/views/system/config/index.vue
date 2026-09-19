<template>
  <!-- 满高两段式布局：上=搜索条件（可折叠），下=列表（占满剩余高度，表格内部滚动） -->
  <div class="h-full flex flex-col gap-4">
    <SearchPanel @search="load" @reset="onReset">
      <v-col cols="12" sm="6" md="3">
        <v-text-field
          v-model="query.configName"
          label="参数名称"
          density="compact"
          hide-details
          clearable
          @keyup.enter="load"
        />
      </v-col>
      <v-col cols="12" sm="6" md="3">
        <v-text-field
          v-model="query.configKey"
          label="参数键名"
          density="compact"
          hide-details
          clearable
          @keyup.enter="load"
        />
      </v-col>
    </SearchPanel>

    <ListPanel title="参数列表">
      <template #actions>
        <v-btn
          v-if="auth.hasPerm('system:config:add')"
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
        item-value="configId"
        :loading="loading"
        hover
      >
        <template #item.actions="{ item }">
          <v-tooltip v-if="auth.hasPerm('system:config:edit')" text="编辑">
            <template #activator="{ props: p }">
              <v-icon
                v-bind="p"
                icon="mdi-pencil"
                size="18"
                class="mr-3 text-secondary"
                @click="openEdit(item)"
              />
            </template>
          </v-tooltip>
          <v-tooltip v-if="auth.hasPerm('system:config:remove')" text="删除">
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
      </v-data-table>
    </ListPanel>

    <v-dialog v-model="dialog" width="520">
      <v-card :title="form.configId ? '修改参数' : '新增参数'" rounded="xl">
        <v-card-text>
          <v-form ref="formRef" @submit.prevent="onSave">
            <v-text-field v-model="form.configName" label="参数名称" :rules="['$required']" />
            <v-text-field
              v-model="form.configKey"
              label="参数键名"
              :disabled="!!form.configId"
              :rules="['$required']"
            />
            <v-text-field v-model="form.configValue" label="参数键值" />
            <v-text-field v-model="form.remark" label="备注" />
          </v-form>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn @click="dialog = false">取消</v-btn>
          <v-btn color="primary" @click="onSave">保存</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

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
import { http } from '@/api/request'
import { clearObject } from '@/utils/object'
import type { SysConfigRow } from '@/types/api'

const auth = useAuthStore()
const rows = ref<SysConfigRow[]>([])
const loading = ref(false)
const dialog = ref(false)
const formRef = ref<{ validate: () => Promise<{ valid: boolean }> } | null>(null)
const query = reactive({ configName: '' as string | null, configKey: '' as string | null })
const form = reactive<Partial<SysConfigRow>>({})
const snack = reactive({ show: false, text: '', color: 'success' })

const headers = [
  { title: 'ID', key: 'configId', width: 70 },
  { title: '参数名称', key: 'configName' },
  { title: '参数键名', key: 'configKey' },
  { title: '参数键值', key: 'configValue' },
  { title: '备注', key: 'remark' },
  { title: '创建时间', key: 'createTime', width: 180 },
  { title: '操作', key: 'actions', fixed: 'end' as const, width: 110, sortable: false },
].map((h) => ({ nowrap: true, ...h }))

function notify(text: string, color: 'success' | 'error' = 'success'): void {
  Object.assign(snack, { show: true, text, color })
}

async function load(): Promise<void> {
  loading.value = true
  try {
    rows.value = await http.get<SysConfigRow[]>('/system/config/list', { params: { ...query } })
  } catch (e) {
    notify(e instanceof Error ? e.message : '加载失败', 'error')
  } finally {
    loading.value = false
  }
}

function onReset(): void {
  query.configName = null
  query.configKey = null
  void load()
}

function openAdd(): void {
  clearObject(form)
  dialog.value = true
}

function openEdit(item: SysConfigRow): void {
  clearObject(form)
  Object.assign(form, item)
  dialog.value = true
}

async function onSave(): Promise<void> {
  const valid = await formRef.value?.validate()
  if (valid && !valid.valid) return
  try {
    if (form.configId) {
      await http.put<null>('/system/config', form)
    } else {
      await http.post<null>('/system/config', form)
    }
    dialog.value = false
    notify('保存成功')
    void load()
  } catch (e) {
    notify(e instanceof Error ? e.message : '保存失败', 'error')
  }
}

async function onDelete(item: SysConfigRow): Promise<void> {
  if (!window.confirm(`确认删除参数「${item.configName}」？`)) return
  try {
    await http.delete<null>(`/system/config/${item.configId}`)
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
