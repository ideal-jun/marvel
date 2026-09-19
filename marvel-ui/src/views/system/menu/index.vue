<template>
  <!-- 满高两段式布局：上=搜索条件（可折叠），下=树表（占满剩余高度，表体内部滚动） -->
  <div class="h-full flex flex-col gap-4">
    <SearchPanel @search="load" @reset="onReset">
      <v-col cols="12" sm="6" md="3">
        <v-text-field
          v-model="query.menuName"
          label="菜单名称"
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

    <ListPanel title="菜单列表">
      <template #actions>
        <v-btn
          v-if="auth.hasPerm('system:menu:add')"
          color="success"
          prepend-icon="mdi-plus"
          rounded="lg"
          @click="openAdd()"
        >
          新增
        </v-btn>
      </template>

      <!-- 树形表格：v-data-table 不支持 children 树形数据，采用「展开集合 + 拍平渲染」
           方案——menuName 列按层级缩进并提供展开箭头，其余列仍由组件库承担 -->
      <v-data-table
        class="flex-1 min-h-0"
        fixed-header
        :headers="headers"
        :items="flatRows"
        item-value="menuId"
        :loading="loading"
        hover
        :items-per-page="-1"
        hide-default-footer
      >
        <template #item.menuName="{ item }">
          <div class="d-flex align-center" :style="{ paddingLeft: item.depth * 28 + 'px' }">
            <v-btn
              v-if="item.children?.length || item.hasChild"
              :icon="expandedIds.has(String(item.menuId)) ? 'mdi-menu-down' : 'mdi-menu-right'"
              size="x-small"
              variant="text"
              class="mr-1"
              @click.stop="toggleExpand(item)"
            />
            <span v-else class="mr-7" />
            <span>{{ item.menuName }}</span>
          </div>
        </template>
        <template #item.menuType="{ item }">
          <v-chip
            size="small"
            label
            :color="item.menuType === 'M' ? 'info' : item.menuType === 'C' ? 'primary' : 'default'"
          >
            {{ MENU_TYPE_TEXT[item.menuType] }}
          </v-chip>
        </template>
        <template #item.status="{ item }">
          <v-chip :color="item.status === '0' ? 'success' : 'error'" size="small" label>
            {{ item.status === '0' ? '正常' : '停用' }}
          </v-chip>
        </template>
        <template #item.actions="{ item }">
          <v-tooltip v-if="auth.hasPerm('system:menu:add')" text="添加子菜单">
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
          <v-tooltip v-if="auth.hasPerm('system:menu:edit')" text="编辑">
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
          <v-tooltip v-if="auth.hasPerm('system:menu:remove')" text="删除">
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
      <v-card :title="form.menuId ? '修改菜单' : '新增菜单'" rounded="xl">
        <v-card-text>
          <v-form ref="formRef" @submit.prevent="onSave">
            <v-select
              v-model="form.parentId"
              :items="parentOptions"
              item-title="menuName"
              item-value="menuId"
              label="上级菜单"
            />
            <v-text-field v-model="form.menuName" label="菜单名称" :rules="['$required']" />
            <v-radio-group v-model="form.menuType" inline label="类型">
              <v-radio label="目录" value="M" />
              <v-radio label="菜单" value="C" />
              <v-radio label="按钮" value="F" />
            </v-radio-group>
            <v-text-field
              v-model="form.path"
              label="路由地址"
              :rules="[['requiredWhen', () => form.menuType !== 'F', '路由地址不能为空']]"
            />
            <v-text-field
              v-if="form.menuType === 'C'"
              v-model="form.component"
              label="组件路径（如 system/user/index）"
              :rules="[['requiredWhen', () => form.menuType === 'C', '组件路径不能为空']]"
            />
            <v-text-field
              v-model="form.perms"
              label="权限标识"
              :rules="[['requiredWhen', () => form.menuType === 'F', '权限标识不能为空']]"
            />
            <v-text-field v-model.number="form.orderNum" label="显示顺序" type="number" />
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
import { computed, onMounted, reactive, ref } from 'vue'
import SearchPanel from '@/components/SearchPanel.vue'
import ListPanel from '@/components/ListPanel.vue'
import { useAuthStore } from '@/stores/auth'
import { http } from '@/api/request'
import { clearObject } from '@/utils/object'
import type { SysMenuRow } from '@/types/api'

const MENU_TYPE_TEXT: Record<SysMenuRow['menuType'], string> = {
  M: '目录',
  C: '菜单',
  F: '按钮',
}

const STATUS_OPTIONS = [
  { title: '正常', value: '0' },
  { title: '停用', value: '1' },
]

/** 拍平后的行：depth 用于 menuName 列缩进 */
type FlatMenuRow = SysMenuRow & { depth: number }

const auth = useAuthStore()
/** 树形菜单数据（/system/menu/tree，children 嵌套） */
const tree = ref<SysMenuRow[]>([])
const loading = ref(false)
const dialog = ref(false)
const formRef = ref<{ validate: () => Promise<{ valid: boolean }> } | null>(null)
/** 展开节点 id 集合；懒加载模式下默认折叠 */
const expandedIds = ref<Set<string>>(new Set())
const parentOptions = ref<SysMenuRow[]>([])
const query = reactive({ menuName: '' as string | null, status: null })
const form = reactive<Partial<SysMenuRow>>({})
const snack = reactive({ show: false, text: '', color: 'success' })

/** 按展开集合把树拍平为表格行：未展开节点的子树不渲染 */
const flatRows = computed<FlatMenuRow[]>(() => {
  const out: FlatMenuRow[] = []
  const walk = (nodes: SysMenuRow[], depth: number): void => {
    for (const n of nodes) {
      out.push({ ...n, depth })
      if (n.children?.length && expandedIds.value.has(String(n.menuId))) {
        walk(n.children, depth + 1)
      }
    }
  }
  walk(tree.value, 0)
  return out
})

const headers = [
  { title: 'ID', key: 'menuId', width: 70 },
  { title: '菜单名称', key: 'menuName' },
  { title: '类型', key: 'menuType', width: 90 },
  { title: '路由地址', key: 'path' },
  { title: '权限标识', key: 'perms' },
  { title: '排序', key: 'orderNum', width: 80 },
  { title: '状态', key: 'status', width: 90 },
  { title: '操作', key: 'actions', fixed: 'end' as const, width: 140, sortable: false },
].map((h) => ({ nowrap: true, ...h }))

function notify(text: string, color: 'success' | 'error' = 'success'): void {
  Object.assign(snack, { show: true, text, color })
}

async function load(): Promise<void> {
  loading.value = true
  try {
    // 有菜单名称 → 后端返回带祖先链的完整树，自动全部展开；
    // 否则懒加载：只取根节点（可带状态过滤），默认折叠
    const searching = !!query.menuName
    tree.value = await http.get<SysMenuRow[]>('/system/menu/tree', {
      params: { menuName: query.menuName || undefined, status: query.status || undefined },
    })
    expandedIds.value = searching ? collectIds(tree.value) : new Set()
    void refreshParentOptions()
  } catch (e) {
    notify(e instanceof Error ? e.message : '加载失败', 'error')
  } finally {
    loading.value = false
  }
}

function onReset(): void {
  query.menuName = null
  query.status = null
  void load()
}

/** 收集树中所有有子节点的 id，用于搜索结果全展开 */
function collectIds(menus: SysMenuRow[]): Set<string> {
  const ids = new Set<string>()
  const walk = (nodes: SysMenuRow[]): void => {
    for (const n of nodes) {
      if (n.children?.length) {
        ids.add(String(n.menuId))
        walk(n.children)
      }
    }
  }
  walk(menus)
  return ids
}

/**
 * 展开/收起节点。首次展开时懒加载子节点（已加载过则使用缓存）。
 * 注意：表格行是 flatRows 拍平时的浅拷贝，子节点必须写回 tree 源节点。
 */
async function toggleExpand(item: SysMenuRow): Promise<void> {
  const key = String(item.menuId)
  const next = new Set(expandedIds.value)
  if (next.has(key)) {
    next.delete(key)
    expandedIds.value = next
    return
  }
  const node = findNode(tree.value, item.menuId)
  if (!node) return
  if (node.hasChild && !Array.isArray(node.children)) {
    try {
      node.children = await http.get<SysMenuRow[]>('/system/menu/tree', {
        params: { parentId: node.menuId, status: query.status || undefined },
      })
    } catch (e) {
      notify(e instanceof Error ? e.message : '子节点加载失败', 'error')
      return
    }
  }
  next.add(key)
  expandedIds.value = next
}

/** 在树中按 id 查找源节点 */
function findNode(menus: SysMenuRow[], id: number): SysMenuRow | undefined {
  for (const m of menus) {
    if (m.menuId === id) return m
    const hit = m.children?.length ? findNode(m.children, id) : undefined
    if (hit) return hit
  }
  return undefined
}

/** 上级菜单下拉选项：取平铺全量列表（懒加载树不完整），按钮不能作为上级 */
async function refreshParentOptions(): Promise<void> {
  const all = await http.get<SysMenuRow[]>('/system/menu/list')
  parentOptions.value = [
    { menuId: 0, menuName: '根目录' } as SysMenuRow,
    ...all.filter((m) => m.menuType !== 'F'),
  ]
}

/** parent 为空表示新增根节点 */
async function openAdd(parent?: SysMenuRow): Promise<void> {
  clearObject(form)
  Object.assign(form, {
    parentId: parent?.menuId ?? 0,
    menuType: 'C',
    orderNum: 0,
    status: '0',
  })
  refreshParentOptions()
  dialog.value = true
}

async function openEdit(item: SysMenuRow): Promise<void> {
  clearObject(form)
  Object.assign(form, item)
  refreshParentOptions()
  dialog.value = true
}

async function onSave(): Promise<void> {
  const valid = await formRef.value?.validate()
  if (valid && !valid.valid) return
  try {
    if (form.menuId) {
      await http.put<null>('/system/menu', form)
    } else {
      await http.post<null>('/system/menu', form)
    }
    dialog.value = false
    notify('保存成功')
    void load()
  } catch (e) {
    notify(e instanceof Error ? e.message : '保存失败', 'error')
  }
}

async function onDelete(item: SysMenuRow): Promise<void> {
  if (!window.confirm(`确认删除菜单「${item.menuName}」？`)) return
  try {
    await http.delete<null>(`/system/menu/${item.menuId}`)
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
