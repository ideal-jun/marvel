<template>
  <!-- 上=字典类型搜索（可折叠），下=主从双栏列表：左类型（点击行选中）、右数据，
       两栏各自占满剩余高度、表体内部滚动 -->
  <div class="h-full flex flex-col gap-4">
    <SearchPanel @search="loadTypes" @reset="onReset">
      <v-col cols="12" sm="6" md="3">
        <v-text-field
          v-model="typeQuery.dictName"
          label="字典名称"
          density="compact"
          hide-details
          clearable
          @keyup.enter="loadTypes"
        />
      </v-col>
      <v-col cols="12" sm="6" md="3">
        <v-text-field
          v-model="typeQuery.dictType"
          label="类型键"
          density="compact"
          hide-details
          clearable
          @keyup.enter="loadTypes"
        />
      </v-col>
      <v-col cols="12" sm="6" md="3">
        <v-select
          v-model="typeQuery.status"
          :items="STATUS_OPTIONS"
          item-props
          item-title="title"
          item-value="value"
          label="状态"
          density="compact"
          hide-details
          clearable
        />
      </v-col>
    </SearchPanel>

    <div class="flex-1 min-h-0 flex flex-col md:flex-row gap-4">
      <!-- 左：字典类型（选中行驱动右栏） -->
      <ListPanel :fill="false" title="字典类型" class="w-full md:w-2/5">
        <template #actions>
          <v-btn
            v-if="auth.hasPerm('system:dict:add')"
            color="success"
            prepend-icon="mdi-plus"
            rounded="lg"
            @click="openTypeAdd"
          >
            新增
          </v-btn>
        </template>

        <v-data-table
          class="flex-1 min-h-0"
          fixed-header
          :headers="typeHeaders"
          :items="types"
          item-value="dictId"
          :loading="typeLoading"
          hover
          :items-per-page="-1"
          hide-default-footer
          :row-props="typeRowProps"
          @click:row="onSelectType"
        >
          <template #item.status="{ item }">
            <v-chip :color="item.status === '0' ? 'success' : 'error'" size="small" label>
              {{ item.status === '0' ? '正常' : '停用' }}
            </v-chip>
          </template>
          <template #item.actions="{ item }">
            <v-icon
              v-if="auth.hasPerm('system:dict:edit')"
              icon="mdi-pencil"
              size="18"
              class="mr-3 text-secondary"
              @click.stop="openTypeEdit(item)"
            />
            <v-icon
              v-if="auth.hasPerm('system:dict:remove')"
              icon="mdi-delete"
              size="18"
              class="text-error"
              @click.stop="onRemoveType(item)"
            />
          </template>
        </v-data-table>
      </ListPanel>

      <!-- 右：选中类型的字典数据 -->
      <ListPanel title="字典数据">
        <template #actions>
          <v-chip v-if="selectedType" size="small" label>{{ selectedType.dictType }}</v-chip>
          <v-text-field
            v-model="dataKeyword"
            label="标签/键值"
            density="compact"
            hide-details
            clearable
            style="max-width: 180px"
            @keyup.enter="loadData"
          />
          <v-btn
            v-if="auth.hasPerm('system:dict:add') && selectedType"
            color="success"
            prepend-icon="mdi-plus"
            rounded="lg"
            @click="openDataAdd"
          >
            新增
          </v-btn>
        </template>

        <v-data-table
          class="flex-1 min-h-0"
          fixed-header
          :headers="dataHeaders"
          :items="dictData"
          item-value="dictCode"
          :loading="dataLoading"
          hover
          :items-per-page="-1"
          hide-default-footer
          :no-data-text="selectedType ? '该类型暂无数据' : '点击左侧字典类型查看数据'"
        >
          <template #item.status="{ item }">
            <v-chip :color="item.status === '0' ? 'success' : 'error'" size="small" label>
              {{ item.status === '0' ? '正常' : '停用' }}
            </v-chip>
          </template>
          <template #item.actions="{ item }">
            <v-icon
              v-if="auth.hasPerm('system:dict:edit')"
              icon="mdi-pencil"
              size="18"
              class="mr-3 text-secondary"
              @click="openDataEdit(item)"
            />
            <v-icon
              v-if="auth.hasPerm('system:dict:remove')"
              icon="mdi-delete"
              size="18"
              class="text-error"
              @click="onRemoveData(item)"
            />
          </template>
        </v-data-table>
      </ListPanel>
    </div>

    <!-- 类型编辑对话框 -->
    <v-dialog v-model="typeDialog" width="480">
      <v-card :title="typeForm.dictId ? '修改字典类型' : '新增字典类型'" rounded="xl">
        <v-card-text>
          <v-text-field v-model="typeForm.dictName" label="字典名称" />
          <v-text-field v-model="typeForm.dictType" label="类型键（如 sys_yes_no）" />
          <v-radio-group v-model="typeForm.status" inline label="状态">
            <v-radio label="正常" value="0" />
            <v-radio label="停用" value="1" />
          </v-radio-group>
          <v-text-field v-model="typeForm.remark" label="备注" />
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn @click="typeDialog = false">取消</v-btn>
          <v-btn color="primary" @click="saveType">保存</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- 数据编辑对话框 -->
    <v-dialog v-model="dataDialog" width="480">
      <v-card :title="dataForm.dictCode ? '修改字典数据' : '新增字典数据'" rounded="xl">
        <v-card-text>
          <v-text-field v-model="dataForm.dictLabel" label="标签" />
          <v-text-field v-model="dataForm.dictValue" label="键值" />
          <v-text-field v-model.number="dataForm.orderNum" label="排序" type="number" />
          <v-radio-group v-model="dataForm.status" inline label="状态">
            <v-radio label="正常" value="0" />
            <v-radio label="停用" value="1" />
          </v-radio-group>
          <v-text-field v-model="dataForm.remark" label="备注" />
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn @click="dataDialog = false">取消</v-btn>
          <v-btn color="primary" @click="saveData">保存</v-btn>
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
import type { SysDictDataRow, SysDictTypeRow } from '@/types/api'

const STATUS_OPTIONS = [
  { title: '正常', value: '0' },
  { title: '停用', value: '1' },
]

interface DictQuery {
  dictName: string,
  dictType: string,
  status: string | null | string[],
}

const auth = useAuthStore()

/* ---------- 字典类型 ---------- */
const types = ref<SysDictTypeRow[]>([])
const typeLoading = ref(false)
const typeDialog = ref(false)
const typeQuery = reactive<DictQuery>({ dictName: '', dictType: '', status: null})
const typeForm = reactive<Partial<SysDictTypeRow>>({})

const typeHeaders = [
  // 左栏较窄：固定列宽保证表头单行（4 字标题含排序图标约需 110px，2 字约 80px）
  { title: '字典名称', key: 'dictName', width: 112 },
  { title: '类型键', key: 'dictType' },
  { title: '状态', key: 'status', width: 80 },
  { title: '操作', key: 'actions', fixed: 'end' as const, width: 92, sortable: false },
].map((h) => ({ nowrap: true, ...h }))

/* ---------- 字典数据（主从） ---------- */
const selectedType = ref<SysDictTypeRow | null>(null)
const dictData = ref<SysDictDataRow[]>([])
const dataLoading = ref(false)
const dataDialog = ref(false)
const dataKeyword = ref('')
const dataForm = reactive<Partial<SysDictDataRow>>({})

const dataHeaders = [
  { title: '标签', key: 'dictLabel' },
  { title: '键值', key: 'dictValue' },
  { title: '排序', key: 'orderNum', width: 90 },
  { title: '状态', key: 'status', width: 90 },
  { title: '操作', key: 'actions', fixed: 'end' as const, width: 100, sortable: false },
].map((h) => ({ nowrap: true, ...h }))

const snack = reactive({ show: false, text: '', color: 'success' })

function notify(text: string, color: 'success' | 'error' = 'success'): void {
  Object.assign(snack, { show: true, text, color })
}

async function loadTypes(): Promise<void> {
  typeLoading.value = true
  try {
    types.value = await http.get<SysDictTypeRow[]>('/system/dict/type/list', { params: { ...typeQuery } })
    // 保持当前选中项；若被删除则清空右侧
    if (selectedType.value) {
      selectedType.value = types.value.find((t) => t.dictId === selectedType.value?.dictId) ?? null
      if (selectedType.value) void loadData()
      else dictData.value = []
    }
  } catch (e) {
    notify(e instanceof Error ? e.message : '加载失败', 'error')
  } finally {
    typeLoading.value = false
  }
}

function onReset(): void {
  typeQuery.dictName = ''
  typeQuery.dictType = ''
  typeQuery.status = null
  void loadTypes()
}

/** 选中行高亮 + 手型光标 */
function typeRowProps({ item }: { item: SysDictTypeRow }): { class: string[] } {
  return {
    class: [
      'cursor-pointer',
      selectedType.value?.dictId === item.dictId ? 'bg-primary/10 font-medium' : '',
    ],
  }
}

/** 点击类型行：选中并加载右侧数据 */
function onSelectType(_e: unknown, row: { item: SysDictTypeRow }): void {
  selectedType.value = row.item
  void loadData()
}

async function loadData(): Promise<void> {
  if (!selectedType.value) return
  dataLoading.value = true
  try {
    dictData.value = await http.get<SysDictDataRow[]>('/system/dict/data/list', {
      params: { dictType: selectedType.value.dictType, keyword: dataKeyword.value || undefined },
    })
  } catch (e) {
    notify(e instanceof Error ? e.message : '加载失败', 'error')
  } finally {
    dataLoading.value = false
  }
}

function openTypeAdd(): void {
  clearObject(typeForm)
  Object.assign(typeForm, { status: '0' })
  typeDialog.value = true
}

function openTypeEdit(item: SysDictTypeRow): void {
  clearObject(typeForm)
  Object.assign(typeForm, item)
  typeDialog.value = true
}

async function saveType(): Promise<void> {
  try {
    if (typeForm.dictId) {
      await http.put<null>('/system/dict/type', typeForm)
    } else {
      await http.post<null>('/system/dict/type', typeForm)
    }
    typeDialog.value = false
    notify('保存成功')
    void loadTypes()
  } catch (e) {
    notify(e instanceof Error ? e.message : '保存失败', 'error')
  }
}

async function onRemoveType(item: SysDictTypeRow): Promise<void> {
  if (!window.confirm(`删除字典类型「${item.dictName}」将同时删除其全部数据，确认？`)) return
  try {
    await http.delete<null>(`/system/dict/type/${item.dictId}`)
    notify('删除成功')
    void loadTypes()
  } catch (e) {
    notify(e instanceof Error ? e.message : '删除失败', 'error')
  }
}

function openDataAdd(): void {
  clearObject(dataForm)
  Object.assign(dataForm, { dictType: selectedType.value?.dictType, orderNum: 1, status: '0' })
  dataDialog.value = true
}

function openDataEdit(item: SysDictDataRow): void {
  clearObject(dataForm)
  Object.assign(dataForm, item)
  dataDialog.value = true
}

async function saveData(): Promise<void> {
  try {
    if (dataForm.dictCode) {
      await http.put<null>('/system/dict/data', dataForm)
    } else {
      await http.post<null>('/system/dict/data', dataForm)
    }
    dataDialog.value = false
    notify('保存成功')
    void loadData()
  } catch (e) {
    notify(e instanceof Error ? e.message : '保存失败', 'error')
  }
}

async function onRemoveData(item: SysDictDataRow): Promise<void> {
  if (!window.confirm(`确认删除字典数据「${item.dictLabel}」？`)) return
  try {
    await http.delete<null>(`/system/dict/data/${item.dictCode}`)
    notify('删除成功')
    void loadData()
  } catch (e) {
    notify(e instanceof Error ? e.message : '删除失败', 'error')
  }
}

onMounted(() => {
  void loadTypes()
})
</script>
