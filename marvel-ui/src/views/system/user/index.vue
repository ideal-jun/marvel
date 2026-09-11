<template>
  <!-- 满高两段式布局：上=搜索条件（可折叠），下=列表（flex-1 占满剩余高度，表格内部滚动） -->
  <div class="h-full flex flex-col gap-4">
    <SearchPanel @search="onSearch" @reset="onReset">
      <v-col cols="12" sm="6" md="3">
        <v-text-field
          v-model="query.username"
          label="用户名"
          density="compact"
          hide-details
          clearable
          @keyup.enter="onSearch"
        />
      </v-col>
      <v-col cols="12" sm="6" md="3">
        <v-text-field
          v-model="query.nickname"
          label="昵称"
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
          v-model="query.deptId"
          :items="deptOptions"
          item-title="deptName"
          item-value="deptId"
          label="部门"
          density="compact"
          hide-details
          clearable
        />
      </v-col>
    </SearchPanel>

    <ListPanel title="用户列表">
      <template #actions>
        <v-btn
          v-if="auth.hasPerm('system:user:add')"
          color="success"
          prepend-icon="mdi-plus"
          rounded="lg"
          @click="openAdd"
        >
          新增
        </v-btn>
        <!-- 批量删除：勾选行后可用，ids 拼逗号复用后端批量删除接口 -->
        <v-btn
          v-if="auth.hasPerm('system:user:remove')"
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
            <v-btn v-bind="props" icon="mdi-refresh" variant="text" rounded="lg" :loading="loading" @click="load" />
          </template>
        </v-tooltip>
        <ColumnSettings v-model:columns="columns" />
      </template>

      <!-- v-table 自身是 flex 列（wrapper flex-1 overflow auto），
           fixed-header 吸顶表头：限高容器内自然形成表体内部滚动 -->
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
        item-value="userId"
        show-select
        hover
        @update:options="onOptions"
      >
        <template #item.status="{ item }">
          <v-chip :color="item.status === '0' ? 'success' : 'error'" size="small" label>
            {{ item.status === '0' ? '正常' : '停用' }}
          </v-chip>
        </template>
        <template #item.actions="{ item }">
          <v-tooltip v-if="auth.hasPerm('system:user:edit')" text="编辑">
            <template #activator="{ props: p }">
              <v-icon v-bind="p" icon="mdi-pencil" size="18" class="mr-3 text-secondary" @click="openEdit(item)" />
            </template>
          </v-tooltip>
          <v-tooltip v-if="auth.hasPerm('system:user:remove')" text="删除">
            <template #activator="{ props: p }">
              <v-icon v-bind="p" icon="mdi-delete" size="18" class="text-error" @click="onDelete(item)" />
            </template>
          </v-tooltip>
        </template>
      </v-data-table-server>
    </ListPanel>

    <v-dialog v-model="dialog" width="560">
      <v-card :title="form.userId ? '修改用户' : '新增用户'" rounded="xl">
        <v-card-text>
          <v-text-field v-model="form.username" label="用户名" :disabled="!!form.userId" />
          <v-text-field v-model="form.nickname" label="昵称" />
          <v-text-field v-if="!form.userId" v-model="form.password" label="初始密码" type="password" />
          <v-text-field v-model="form.phone" label="手机号" />
          <v-select
            v-model="form.deptId"
            :items="deptOptions"
            item-title="deptName"
            item-value="deptId"
            label="部门"
          />
          <v-select
            v-model="roleIds"
            :items="roleOptions"
            item-title="roleName"
            item-value="roleId"
            label="角色"
            multiple
            chips
          />
          <v-radio-group v-model="form.status" inline label="状态">
            <v-radio label="正常" value="0" />
            <v-radio label="停用" value="1" />
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
import { computed, onMounted, reactive, ref } from 'vue'
import SearchPanel from '@/components/SearchPanel.vue'
import ListPanel from '@/components/ListPanel.vue'
import ColumnSettings, { type ColumnDef } from '@/components/ColumnSettings.vue'
import { useAuthStore } from '@/stores/auth'
import { http } from '@/api/request'
import { clearObject } from '@/utils/object'
import type {
  PageResult,
  SysDeptRow,
  SysRoleOption,
  SysUserRow,
  UserDetailResult,
} from '@/types/api'

const STATUS_OPTIONS = [
  { title: '正常', value: '0' },
  { title: '停用', value: '1' },
]

const auth = useAuthStore()
const rows = ref<SysUserRow[]>([])
const total = ref(0)
const loading = ref(false)
const dialog = ref(false)
const roleIds = ref<number[]>([])
const roleOptions = ref<SysRoleOption[]>([])
const deptOptions = ref<SysDeptRow[]>([])
/** 表格勾选的行：Vuetify 4 选中模型存 item-value 值（即 userId 数组） */
const selected = ref<number[]>([])

interface UserQuery {
  pageNum: number
  pageSize: number
  username: string | null
  nickname: string | null
  status: string | null
  deptId: number | null
}
const query = reactive<UserQuery>({
  pageNum: 1,
  pageSize: 10,
  username: null,
  nickname: null,
  status: null,
  deptId: null,
})

/** 新增/编辑表单：userId 为空表示新增 */
const form = reactive<Partial<SysUserRow> & { password?: string }>({})

const snack = reactive({ show: false, text: '', color: 'success' })

/** 列定义（列设置弹层可调显隐/固定/顺序），表格 headers 由其计算 */
const columns = ref<ColumnDef[]>([
  { key: 'userId', title: 'ID', width: 70, visible: true },
  { key: 'username', title: '用户名', width: 140, visible: true },
  { key: 'nickname', title: '昵称', visible: true },
  { key: 'phone', title: '手机号', visible: true },
  { key: 'status', title: '状态', width: 90, visible: true },
  { key: 'createTime', title: '创建时间', width: 180, visible: true },
  { key: 'actions', title: '操作', width: 110, sortable: false, visible: true },
])

const headers = computed(() =>
  columns.value
    .filter((c) => c.visible)
    .map((c) => ({
      title: c.title,
      key: c.key,
      width: c.width,
      sortable: c.sortable,
      fixed: c.fixed ? ('start' as const) : undefined,
      // 表头单行：nowrap 为 Vuetify 官方属性，列不足时表格横向滚动而非换行
      nowrap: true,
    })),
)

function notify(text: string, color: 'success' | 'error' = 'success'): void {
  Object.assign(snack, { show: true, text, color })
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const page = await http.get<PageResult<SysUserRow>>('/system/user/page', { params: { ...query } })
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
  query.username = null
  query.nickname = null
  query.status = null
  query.deptId = null
  onSearch()
}

function onOptions(opts: { page: number; itemsPerPage: number }): void {
  query.pageNum = opts.page
  query.pageSize = opts.itemsPerPage
  void load()
}

async function openAdd(): Promise<void> {
  clearObject(form)
  Object.assign(form, { status: '0' })
  roleIds.value = []
  dialog.value = true
  await loadRoleOptions()
}

async function openEdit(item: SysUserRow): Promise<void> {
  const detail = await http.get<UserDetailResult>(`/system/user/${item.userId}`)
  clearObject(form)
  Object.assign(form, detail.user)
  roleIds.value = detail.roleIds
  dialog.value = true
  await loadRoleOptions()
}

async function loadRoleOptions(): Promise<void> {
  roleOptions.value = await http.get<SysRoleOption[]>('/system/user/options/roles')
}

async function loadDeptOptions(): Promise<void> {
  deptOptions.value = await http.get<SysDeptRow[]>('/system/dept/list')
}

async function onSave(): Promise<void> {
  try {
    const params = new URLSearchParams(roleIds.value.map((id) => ['roleIds', String(id)]))
    if (form.userId) {
      await http.put<null>(`/system/user?${params.toString()}`, form)
    } else {
      await http.post<null>(`/system/user?${params.toString()}`, form)
    }
    dialog.value = false
    notify('保存成功')
    void load()
  } catch (e) {
    notify(e instanceof Error ? e.message : '保存失败', 'error')
  }
}

async function onDelete(item: SysUserRow): Promise<void> {
  if (!window.confirm(`确认删除用户「${item.username}」？`)) return
  try {
    await http.delete<null>(`/system/user/${item.userId}`)
    notify('删除成功')
    void load()
  } catch (e) {
    notify(e instanceof Error ? e.message : '删除失败', 'error')
  }
}

/** 批量删除勾选用户（后端 DELETE /{userIds} 支持逗号分隔多 id，超管受保护） */
async function onBatchDelete(): Promise<void> {
  if (!selected.value.length) return
  if (!window.confirm(`确认删除选中的 ${selected.value.length} 个用户？`)) return
  try {
    await http.delete<null>(`/system/user/${selected.value.join(',')}`)
    selected.value = []
    notify('删除成功')
    void load()
  } catch (e) {
    notify(e instanceof Error ? e.message : '删除失败', 'error')
  }
}

onMounted(() => {
  void load()
  void loadDeptOptions()
})
</script>
