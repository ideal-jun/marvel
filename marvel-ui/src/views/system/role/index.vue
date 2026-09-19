<template>
  <!-- 满高两段式布局：上=搜索条件（可折叠），下=列表（占满剩余高度，表格内部滚动） -->
  <div class="h-full flex flex-col gap-4">
    <SearchPanel @search="onSearch" @reset="onReset">
      <v-col cols="12" sm="6" md="3">
        <v-text-field
          v-model="query.roleName"
          label="角色名称"
          density="compact"
          hide-details
          clearable
          @keyup.enter="onSearch"
        />
      </v-col>
      <v-col cols="12" sm="6" md="3">
        <v-text-field
          v-model="query.roleKey"
          label="权限字符"
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
    </SearchPanel>

    <ListPanel title="角色列表">
      <template #actions>
        <v-btn
          v-if="auth.hasPerm('system:role:add')"
          color="success"
          prepend-icon="mdi-plus"
          rounded="lg"
          @click="openAdd"
        >
          新增
        </v-btn>
      </template>

      <v-data-table-server
        class="flex-1 min-h-0"
        fixed-header
        :headers="headers"
        :items="rows"
        :items-length="total"
        :items-per-page="query.pageSize"
        :page="query.pageNum"
        :loading="loading"
        item-value="roleId"
        hover
        @update:options="onOptions"
      >
        <template #item.status="{ item }">
          <v-chip :color="item.status === '0' ? 'success' : 'error'" size="small" label>
            {{ item.status === '0' ? '正常' : '停用' }}
          </v-chip>
        </template>
        <template #item.actions="{ item }">
          <v-tooltip v-if="auth.hasPerm('system:role:edit')" text="编辑">
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
          <v-tooltip v-if="auth.hasPerm('system:role:remove')" text="删除">
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

    <v-dialog v-model="dialog" width="560">
      <v-card :title="form.roleId ? '修改角色' : '新增角色'" rounded="xl">
        <v-card-text>
          <v-form ref="formRef" @submit.prevent="onSave">
            <v-text-field v-model="form.roleName" label="角色名称" :rules="['$required']" />
            <v-text-field v-model="form.roleKey" label="权限字符" :rules="['$required']" />
            <v-text-field v-model.number="form.roleSort" label="显示顺序" type="number" />
            <!-- 菜单权限树：勾选后随表单提交 -->
            <div class="text-caption text-secondary mb-1">菜单权限</div>
            <v-treeview
              v-model="menuIds"
              :items="menuTree"
              item-value="id"
              item-title="menuName"
              select-strategy="classic"
              open-on-click
              density="compact"
            />
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
import type { PageResult, SysMenuRow, SysRoleRow } from '@/types/api'

/** 菜单树节点（Vuetify Treeview 结构） */
interface MenuTreeItem {
  id: number
  menuName: string
  children: MenuTreeItem[] | null
}

const STATUS_OPTIONS = [
  { title: '正常', value: '0' },
  { title: '停用', value: '1' },
]

const auth = useAuthStore()
const rows = ref<SysRoleRow[]>([])
const total = ref(0)
const loading = ref(false)
const dialog = ref(false)
const formRef = ref<{ validate: () => Promise<{ valid: boolean }> } | null>(null)
const menuIds = ref<number[]>([])
const menuTree = ref<MenuTreeItem[]>([])

interface RoleQuery {
  pageNum: number
  pageSize: number
  roleName: string | null
  roleKey: string | null
  status: string | null
}
const query = reactive<RoleQuery>({
  pageNum: 1,
  pageSize: 10,
  roleName: null,
  roleKey: null,
  status: null,
})
const form = reactive<Partial<SysRoleRow> & { menuIds?: number[] }>({})
const snack = reactive({ show: false, text: '', color: 'success' })

const headers = [
  { title: 'ID', key: 'roleId', width: 70 },
  { title: '角色名称', key: 'roleName' },
  { title: '权限字符', key: 'roleKey' },
  { title: '排序', key: 'roleSort', width: 80 },
  { title: '状态', key: 'status', width: 90 },
  { title: '操作', key: 'actions', fixed: 'end' as const, width: 110, sortable: false },
].map((h) => ({ nowrap: true, ...h }))

function notify(text: string, color: 'success' | 'error' = 'success'): void {
  Object.assign(snack, { show: true, text, color })
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const page = await http.get<PageResult<SysRoleRow>>('/system/role/page', {
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

/** 搜索/回车查询统一回到第一页，避免停留在越页码 */
function onSearch(): void {
  query.pageNum = 1
  void load()
}

function onReset(): void {
  query.roleName = null
  query.roleKey = null
  query.status = null
  onSearch()
}

function onOptions(opts: { page: number; itemsPerPage: number }): void {
  query.pageNum = opts.page
  query.pageSize = opts.itemsPerPage
  void load()
}

/** 平铺菜单列表组树（过滤按钮类型） */
function toTree(menus: SysMenuRow[], parentId: number): MenuTreeItem[] {
  return menus
    .filter((m) => m.parentId === parentId && m.menuType !== 'F')
    .map((m) => ({ id: m.menuId, menuName: m.menuName, children: toTree(menus, m.menuId) }))
}

async function openAdd(): Promise<void> {
  clearObject(form)
  Object.assign(form, { roleSort: 0, status: '0' })
  menuIds.value = []
  dialog.value = true
  await loadMenus()
}

async function openEdit(item: SysRoleRow): Promise<void> {
  clearObject(form)
  Object.assign(form, item)
  menuIds.value = []
  dialog.value = true
  const [menus, ids] = await Promise.all([
    http.get<SysMenuRow[]>('/system/menu/list'),
    http.get<number[]>(`/system/role/${item.roleId}/menuIds`),
  ])
  menuTree.value = toTree(menus, 0)
  menuIds.value = ids
}

async function loadMenus(): Promise<void> {
  const menus = await http.get<SysMenuRow[]>('/system/menu/list')
  menuTree.value = toTree(menus, 0)
}

async function onSave(): Promise<void> {
  const valid = await formRef.value?.validate()
  if (valid && !valid.valid) return
  try {
    const payload = { ...form, menuIds: menuIds.value }
    if (form.roleId) {
      await http.put<null>('/system/role', payload)
    } else {
      await http.post<null>('/system/role', payload)
    }
    dialog.value = false
    notify('保存成功')
    void load()
  } catch (e) {
    notify(e instanceof Error ? e.message : '保存失败', 'error')
  }
}

async function onDelete(item: SysRoleRow): Promise<void> {
  if (!window.confirm(`确认删除角色「${item.roleName}」？`)) return
  try {
    await http.delete<null>(`/system/role/${item.roleId}`)
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
