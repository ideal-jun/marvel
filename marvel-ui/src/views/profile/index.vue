<template>
  <div class="h-full overflow-y-auto">
    <v-row dense>
      <!-- 左：个人信息（头像 + 只读资料） -->
      <v-col cols="12" md="4" lg="3">
        <v-card>
          <v-card-title class="text-h6 font-bold">个人信息</v-card-title>
          <v-divider />
          <v-card-text class="flex flex-col items-center">
            <div class="relative mb-4">
              <v-avatar size="110" color="primary">
                <v-img v-if="form.avatar" :src="form.avatar" alt="avatar" />
                <span v-else class="text-h4 font-weight-bold">{{ initial }}</span>
              </v-avatar>
              <v-btn
                icon="mdi-camera"
                size="small"
                color="primary"
                class="absolute -bottom-1 -right-1"
                :loading="uploading"
                @click="pickAvatar"
              />
              <input
                ref="fileInput"
                type="file"
                accept="image/*"
                class="hidden"
                @change="onAvatarChange"
              />
            </div>

            <div class="w-full">
              <div
                v-for="(item, i) in profileItems"
                :key="item.label"
                class="flex items-center justify-between gap-3 py-3"
                :class="i > 0 ? 'border-t border-[rgba(var(--v-theme-on-surface),0.08)]' : ''"
              >
                <div
                  class="flex items-center gap-2 text-body-2 text-medium-emphasis whitespace-nowrap"
                >
                  <v-icon :icon="item.icon" size="18" />
                  <span>{{ item.label }}</span>
                </div>
                <div class="text-body-2 font-medium text-right break-all">
                  {{ item.value || '—' }}
                </div>
              </div>
            </div>
          </v-card-text>
        </v-card>
      </v-col>

      <!-- 右：可编辑资料 / 修改密码（按 tab 条件渲染，避免 v-window 裁剪首行标签） -->
      <v-col cols="12" md="8" lg="9">
        <v-card>
          <v-card-title class="text-h6 font-bold">{{ tabTitle }}</v-card-title>
          <v-tabs v-model="tab" color="primary">
            <v-tab value="basic">基本资料</v-tab>
            <v-tab value="pwd">修改密码</v-tab>
          </v-tabs>
          <v-divider />
          <v-card-text>
            <v-form v-if="tab === 'basic'" ref="basicForm" @submit.prevent>
              <v-text-field
                v-model="form.nickname"
                label="用户昵称"
                :rules="['$required']"
                class="mb-4"
              />
              <v-text-field
                v-model="form.phone"
                label="手机号码"
                :rules="['$required', 'phone']"
                class="mb-4"
              />
              <v-text-field
                v-model="form.email"
                label="邮箱"
                :rules="['$required', 'email']"
                class="mb-4"
              />
              <div class="text-body-2 text-medium-emphasis mb-1">性别</div>
              <v-radio-group v-model="form.sex" inline hide-details class="mt-0">
                <v-radio label="男" value="0" />
                <v-radio label="女" value="1" />
              </v-radio-group>
              <div class="flex gap-2 mt-8">
                <v-btn color="primary" rounded="lg" :loading="saving" @click="saveProfile"
                  >保存</v-btn
                >
                <v-btn color="error" variant="tonal" rounded="lg" @click="onClose">关闭</v-btn>
              </div>
            </v-form>

            <v-form v-else @submit.prevent>
              <v-text-field
                v-model="pwd.oldPassword"
                label="原密码"
                type="password"
                :rules="['$required']"
                class="mb-4"
              />
              <v-text-field
                v-model="pwd.newPassword"
                label="新密码"
                type="password"
                hint="8-32 位，且同时包含字母与数字"
                persistent-hint
                :rules="['$required']"
                class="mb-4"
              />
              <v-text-field
                v-model="pwd.confirmPassword"
                label="确认新密码"
                type="password"
                :rules="['$required']"
                class="mb-4"
              />
              <div class="flex gap-2 mt-8">
                <v-btn color="primary" rounded="lg" :loading="changing" @click="savePassword"
                  >保存</v-btn
                >
                <v-btn color="error" variant="tonal" rounded="lg" @click="onClose">关闭</v-btn>
              </div>
            </v-form>
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>

    <v-snackbar v-model="snack.show" :color="snack.color" timeout="3000">{{
      snack.text
    }}</v-snackbar>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { http } from '@/api/request'
import { useAuthStore } from '@/stores/auth'
import type { ProfileInfo } from '@/types/api'

const router = useRouter()
const auth = useAuthStore()

const tab = ref('basic')
const info = ref<ProfileInfo | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const basicForm = ref<{ validate: () => Promise<{ valid: boolean }> } | null>(null)
const uploading = ref(false)
const saving = ref(false)
const changing = ref(false)
const snack = reactive({ show: false, text: '', color: 'success' })

const form = reactive({
  nickname: '',
  email: '',
  phone: '',
  sex: null as string | null,
  avatar: null as string | null,
})

const pwd = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const initial = computed<string>(() => (form.nickname || info.value?.username || 'U').slice(0, 1))
const tabTitle = computed<string>(() => (tab.value === 'basic' ? '基本资料' : '修改密码'))

/** LocalDateTime ISO 串转「yyyy-MM-dd HH:mm:ss」 */
function formatTime(value?: string | null): string | undefined {
  return value ? value.replace('T', ' ').slice(0, 19) : undefined
}

/** 左侧只读信息项 */
const profileItems = computed(() => [
  { label: '用户名称', icon: 'mdi-account-outline', value: info.value?.username },
  { label: '手机号码', icon: 'mdi-cellphone', value: info.value?.phone },
  { label: '用户邮箱', icon: 'mdi-email-outline', value: info.value?.email },
  { label: '所属部门', icon: 'mdi-sitemap-outline', value: info.value?.deptName },
  { label: '所属角色', icon: 'mdi-account-key-outline', value: auth.roles.join('、') },
  {
    label: '创建日期',
    icon: 'mdi-calendar-clock-outline',
    value: formatTime(info.value?.createTime),
  },
])

function notify(text: string, color: 'success' | 'error' = 'success'): void {
  Object.assign(snack, { show: true, text, color })
}

async function loadProfile(): Promise<void> {
  try {
    const data = await http.get<ProfileInfo>('/system/user/profile')
    info.value = data
    form.nickname = data.nickname ?? ''
    form.email = data.email ?? ''
    form.phone = data.phone ?? ''
    form.sex = data.sex ?? null
    form.avatar = data.avatar ?? null
    // app bar 头像同步
    auth.avatar = data.avatar ?? ''
  } catch (e) {
    notify(e instanceof Error ? e.message : '加载失败', 'error')
  }
}

async function saveProfile(): Promise<void> {
  const valid = await basicForm.value?.validate()
  if (valid && !valid.valid) return
  saving.value = true
  try {
    await http.put<null>('/system/user/profile', { ...form })
    // 顶栏昵称同步刷新
    auth.nickname = form.nickname
    auth.avatar = form.avatar ?? ''
    notify('资料已保存')
    await loadProfile()
  } catch (e) {
    notify(e instanceof Error ? e.message : '保存失败', 'error')
  } finally {
    saving.value = false
  }
}

function pickAvatar(): void {
  fileInput.value?.click()
}

async function onAvatarChange(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  uploading.value = true
  try {
    const body = new FormData()
    body.append('file', file)
    const res = await http.post<{ url: string; path: string }>('/infra/file/avatar', body)
    form.avatar = res.url
    auth.avatar = res.url
    // 头像即时生效：上传成功后直接落库
    await http.put<null>('/system/user/profile', { ...form })
    notify('头像已更新')
    await loadProfile()
  } catch (e) {
    notify(e instanceof Error ? e.message : '头像上传失败', 'error')
  } finally {
    uploading.value = false
  }
}

async function savePassword(): Promise<void> {
  if (!pwd.oldPassword || !pwd.newPassword) {
    notify('请填写原密码与新密码', 'error')
    return
  }
  if (pwd.newPassword !== pwd.confirmPassword) {
    notify('两次输入的新密码不一致', 'error')
    return
  }
  changing.value = true
  try {
    await http.put<null>('/system/user/profile/password', null, {
      params: { oldPassword: pwd.oldPassword, newPassword: pwd.newPassword },
    })
    pwd.oldPassword = ''
    pwd.newPassword = ''
    pwd.confirmPassword = ''
    notify('密码修改成功')
  } catch (e) {
    notify(e instanceof Error ? e.message : '修改失败', 'error')
  } finally {
    changing.value = false
  }
}

/** 关闭：返回上一页（无历史时回首页） */
function onClose(): void {
  if (window.history.length > 1) router.back()
  else void router.push('/')
}

onMounted(() => {
  void loadProfile()
})
</script>
