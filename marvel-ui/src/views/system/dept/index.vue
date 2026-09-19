<template>
  <!-- 满高两段式布局：上=搜索条件（可折叠），下=机构树（占满剩余高度，表格内部滚动） -->
  <div class="h-full flex flex-col gap-4">
    <SearchPanel @search="load" @reset="onReset">
      <v-col cols="12" sm="6" md="3">
        <v-text-field
          v-model="query.deptName"
          label="机构名称"
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

    <ListPanel title="机构列表">
      <template #actions>
        <v-tooltip :text="allExpanded ? '全部折叠' : '全部展开'" location="bottom">
          <template #activator="{ props: p }">
            <v-btn
              v-bind="p"
              :icon="allExpanded ? 'mdi-unfold-less-horizontal' : 'mdi-unfold-more-horizontal'"
              variant="text"
              density="comfortable"
              rounded="lg"
              @click="toggleAll"
            />
          </template>
        </v-tooltip>
        <v-btn
          v-if="auth.hasPerm('system:dept:add')"
          color="success"
          prepend-icon="mdi-plus"
          rounded="lg"
          @click="openAdd()"
        >
          新增
        </v-btn>
      </template>

      <v-data-table
        class="flex-1 min-h-0"
        fixed-header
        :headers="headers"
        :items="visibleRows"
        item-value="deptId"
        :loading="loading"
        hover
      >
        <!-- 机构名称即树列：按层级缩进 + 展开/折叠箭头 -->
        <template #item.deptName="{ item }">
          <div class="flex items-center" :style="{ paddingLeft: item.depth * 20 + 'px' }">
            <v-btn
              v-if="item.hasChildren"
              :icon="item.expanded ? 'mdi-chevron-down' : 'mdi-chevron-right'"
              size="x-small"
              variant="text"
              density="comfortable"
              class="mr-1"
              @click.stop="toggleExpand(item)"
            />
            <span v-else class="inline-block w-6 shrink-0" />
            <!-- 仅根节点显示楼宇图标；其余节点不再显示拐弯箭头 -->
            <v-icon
              v-if="item.parentId === 0"
              icon="mdi-office-building"
              size="16"
              class="mr-1 text-medium-emphasis"
            />
            <span>{{ item.deptName }}</span>
          </div>
        </template>
        <template #item.status="{ item }">
          <v-chip :color="item.status === '0' ? 'success' : 'error'" size="small" label>
            {{ item.status === '0' ? '正常' : '停用' }}
          </v-chip>
        </template>
        <template #item.actions="{ item }">
          <!-- 层级最多到部门：第 3 层（部门）不再提供添加下级 -->
          <v-tooltip
            v-if="auth.hasPerm('system:dept:add') && item.depth < MAX_DEPTH"
            text="添加下级机构"
          >
            <template #activator="{ props: p }">
              <v-icon
                v-bind="p"
                icon="mdi-plus"
                size="18"
                class="mr-3 text-secondary"
                @click="openAdd(item)"
              />
            </template>
          </v-tooltip>
          <v-tooltip v-if="auth.hasPerm('system:dept:edit')" text="编辑">
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
          <v-tooltip v-if="auth.hasPerm('system:dept:remove')" text="删除">
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

    <v-dialog v-model="dialog" width="560">
      <v-card :title="form.deptId ? '修改机构' : '新增机构'" rounded="xl">
        <v-card-text>
          <v-form ref="formRef" @submit.prevent>
            <!-- 上级机构：树形下拉（组件 TreeSelect，内部为 v-menu + VTreeview）。
                 工具栏「新增」与「编辑」可改；行内「＋」新增时固定为所点机构并禁用 -->
            <TreeSelect
              v-if="showParentField"
              class="mb-1"
              :model-value="form.parentId ?? null"
              :items="parentTree"
              item-title="deptName"
              item-value="deptId"
              label="上级机构"
              placeholder="请选择上级机构"
              :disabled="parentLocked"
              @update:model-value="onParentChange"
            />
            <v-text-field v-model="form.deptName" label="机构名称" :rules="['$required']" />
            <v-text-field v-model.number="form.orderNum" label="显示顺序" type="number" />
            <v-text-field v-model="form.leader" label="负责人" />
            <v-text-field v-model="form.phone" label="联系电话" />
            <v-radio-group v-model="form.status" inline label="状态">
              <v-radio label="正常" value="0" />
              <v-radio label="停用" value="1" />
            </v-radio-group>
          </v-form>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn @click="dialog = false">取消</v-btn>
          <v-btn color="primary" :loading="saving" @click="onSave">保存</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-snackbar v-model="snack.show" :color="snack.color" timeout="3000">{{
      snack.text
    }}</v-snackbar>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import SearchPanel from '@/components/SearchPanel.vue'
import TreeSelect from '@/components/TreeSelect.vue'
import ListPanel from '@/components/ListPanel.vue'
import { useAuthStore } from '@/stores/auth'
import { http } from '@/api/request'
import { clearObject } from '@/utils/object'
import type { SysDeptRow } from '@/types/api'

/** 树形表格行：在原始机构上附加层级信息 */
interface DeptRow extends SysDeptRow {
  depth: number
  hasChildren: boolean
  expanded: boolean
}

/** 机构表单：parentId 允许 null，表示「未选上级」 */
interface DeptForm extends Omit<Partial<SysDeptRow>, 'parentId'> {
  parentId?: number | null
}

const STATUS_OPTIONS = [
  { title: '正常', value: '0' },
  { title: '停用', value: '1' },
]

/** 机构最大层级深度：0=顶级机构，1=分公司，2=部门（部门下不再有下级） */
const MAX_DEPTH = 2

/** 机构层级深度：根（ancestors=0）为 0，每多一级 +1 */
function levelOf(dept: SysDeptRow): number {
  return (dept.ancestors ?? '').split(',').length - 1
}

const auth = useAuthStore()
const rows = ref<SysDeptRow[]>([])
/** 弹窗「上级机构」下拉的数据源：全量列表，不受当前搜索条件影响 */
const allDepts = ref<SysDeptRow[]>([])
const expandedIds = ref<Set<number>>(new Set())
const loading = ref(false)
const saving = ref(false)
const dialog = ref(false)
const formRef = ref<{ validate: () => Promise<{ valid: boolean }> } | null>(null)
const query = reactive({ deptName: '' as string | null, status: null })
const form = reactive<DeptForm>({})
const snack = reactive({ show: false, text: '', color: 'success' })

const headers = [
  { title: 'ID', key: 'deptId', width: 70 },
  { title: '机构名称', key: 'deptName' },
  { title: '负责人', key: 'leader', width: 120 },
  { title: '联系电话', key: 'phone', width: 150 },
  { title: '排序', key: 'orderNum', width: 80 },
  { title: '状态', key: 'status', width: 90 },
  { title: '创建时间', key: 'createTime', width: 180 },
  { title: '操作', key: 'actions', fixed: 'end' as const, width: 150, sortable: false },
].map((h) => ({ nowrap: true, ...h }))

/** 由平铺列表按 parentId 组树后，依当前展开状态压平为可渲染行 */
const visibleRows = computed<DeptRow[]>(() => {
  const { childrenOf, roots } = groupByParent(rows.value)
  const out: DeptRow[] = []
  const walk = (list: SysDeptRow[], depth: number): void => {
    for (const dept of byOrder(list)) {
      const children = childrenOf.get(dept.deptId) ?? []
      const expanded = expandedIds.value.has(dept.deptId)
      out.push({ ...dept, depth, hasChildren: children.length > 0, expanded })
      if (children.length && expanded) walk(children, depth + 1)
    }
  }
  walk(roots, 0)
  return out
})

/** 编辑顶级机构时上级固定（根不可移动），隐藏该字段 */
const isRootNode = computed(() => form.deptId != null && form.parentId === 0)
const showParentField = computed(() => !isRootNode.value)

/** 树形下拉节点：只用 deptId/deptName/children，不塞自定义 props（会干扰 Vuetify 内部 itemProps） */
type ParentTreeNode = {
  deptId: number
  deptName: string
  children?: ParentTreeNode[]
}

/**
 * 上级机构树（供 VTreeview 使用）：排除自身及其后代（防成环）；
 * 层级已达「部门」的节点保留展示但置灰，不能作为上级。
 */
const parentTree = computed<ParentTreeNode[]>(() => {
  const excluded = new Set<number>()
  if (form.deptId) {
    excluded.add(form.deptId)
    const target = String(form.deptId)
    allDepts.value.forEach((d) => {
      if ((d.ancestors ?? '').split(',').some((p) => p.trim() === target)) excluded.add(d.deptId)
    })
  }
  const { childrenOf, roots } = groupByParent(allDepts.value)
  const build = (list: SysDeptRow[]): ParentTreeNode[] =>
    byOrder(list)
      // 只保留「可作上级」的节点（层级 < 部门）：部门不能作为上级，直接不列入
      .filter((d) => !excluded.has(d.deptId) && levelOf(d) < MAX_DEPTH)
      .map((d) => {
        const children = childrenOf.get(d.deptId)
        return {
          deptId: d.deptId,
          deptName: d.deptName,
          children: children ? build(children) : undefined,
        }
      })
  return build(roots)
})

/** 上级机构是否锁定：行内「＋」新增时固定为所点机构，不可改 */
const parentLocked = ref(false)

/** 树形下拉回写：单选只会是单个 id，统一规整为 number | null */
function onParentChange(value: string | number | null): void {
  form.parentId = value == null ? null : Number(value)
}

const allExpanded = computed(
  () => rows.value.length > 0 && rows.value.every((d) => expandedIds.value.has(d.deptId)),
)

/** 按 parentId 分组；父级不在集合中（被搜索过滤掉或非本人可见）的视为根节点 */
function groupByParent(list: SysDeptRow[]): {
  childrenOf: Map<number, SysDeptRow[]>
  roots: SysDeptRow[]
} {
  const byId = new Map<number, SysDeptRow>()
  list.forEach((d) => byId.set(d.deptId, d))
  const childrenOf = new Map<number, SysDeptRow[]>()
  const roots: SysDeptRow[] = []
  for (const dept of list) {
    const parent = byId.get(dept.parentId)
    if (parent && parent.deptId !== dept.deptId) {
      const siblings = childrenOf.get(parent.deptId)
      if (siblings) siblings.push(dept)
      else childrenOf.set(parent.deptId, [dept])
    } else {
      roots.push(dept)
    }
  }
  return { childrenOf, roots }
}

function byOrder(list: SysDeptRow[]): SysDeptRow[] {
  return [...list].sort((a, b) => (a.orderNum ?? 0) - (b.orderNum ?? 0))
}

function notify(text: string, color: 'success' | 'error' = 'success'): void {
  Object.assign(snack, { show: true, text, color })
}

async function load(): Promise<void> {
  loading.value = true
  try {
    rows.value = await http.get<SysDeptRow[]>('/system/dept/list', { params: { ...query } })
    // 默认全部展开，层级结构一眼可见
    expandedIds.value = new Set(rows.value.map((d) => d.deptId))
  } catch (e) {
    notify(e instanceof Error ? e.message : '加载失败', 'error')
  } finally {
    loading.value = false
  }
}

function onReset(): void {
  query.deptName = null
  query.status = null
  void load()
}

function toggleExpand(node: DeptRow): void {
  const next = new Set(expandedIds.value)
  if (next.has(node.deptId)) next.delete(node.deptId)
  else next.add(node.deptId)
  expandedIds.value = next
}

function toggleAll(): void {
  expandedIds.value = allExpanded.value ? new Set() : new Set(rows.value.map((d) => d.deptId))
}

async function openAdd(parent?: SysDeptRow): Promise<void> {
  clearObject(form)
  allDepts.value = await http.get<SysDeptRow[]>('/system/dept/list')
  const root = allDepts.value.find((d) => d.parentId === 0)
  // 行内「＋」：上级固定为所点机构且禁用；工具栏「新增」：默认顶级机构，可改
  parentLocked.value = !!parent
  Object.assign(form, {
    parentId: parent?.deptId ?? root?.deptId ?? null,
    orderNum: 0,
    status: '0',
  })
  dialog.value = true
}

async function openEdit(item: SysDeptRow): Promise<void> {
  clearObject(form)
  allDepts.value = await http.get<SysDeptRow[]>('/system/dept/list')
  // 编辑支持调整上级机构
  parentLocked.value = false
  Object.assign(form, item)
  dialog.value = true
}

async function onSave(): Promise<void> {
  const valid = await formRef.value?.validate()
  if (valid && !valid.valid) return
  if (!isRootNode.value && !form.parentId) {
    notify('请选择上级机构', 'error')
    return
  }
  saving.value = true
  const payload = { ...form, parentId: isRootNode.value ? 0 : (form.parentId ?? 0) }
  try {
    if (form.deptId) {
      await http.put<null>('/system/dept', payload)
    } else {
      await http.post<null>('/system/dept', payload)
    }
    dialog.value = false
    notify('保存成功')
    void load()
  } catch (e) {
    notify(e instanceof Error ? e.message : '保存失败', 'error')
  } finally {
    saving.value = false
  }
}

async function onDelete(item: SysDeptRow): Promise<void> {
  if (!window.confirm(`确认删除机构「${item.deptName}」？`)) return
  try {
    await http.delete<null>(`/system/dept/${item.deptId}`)
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
