<template>
  <v-main class="bg-brand-gradient flex items-center justify-center h-full">
    <v-card class="p-8 elevation-0" width="400">
      <div class="text-center mb-8">
        <v-avatar color="primary" size="52" class="mb-3">
          <v-icon icon="mdi-hexagon-multiple" size="30" />
        </v-avatar>
        <div class="text-h5 font-bold">Marvel 后台管理系统</div>
        <div class="text-body-2 text-secondary mt-1">Modular Monolith · Spring Boot 4</div>
      </div>

      <!-- 登录失败提示（组件库 VAlert，替代原生 alert） -->
      <v-alert
        v-model="showError"
        type="error"
        variant="tonal"
        density="compact"
        closable
        class="mb-4"
      >
        {{ errorMessage }}
      </v-alert>

      <v-form @submit.prevent="onSubmit">
        <v-text-field
          v-model="form.username"
          label="用户名"
          prepend-inner-icon="mdi-account-outline"
          autocomplete="username"
          :rules="[rules.required]"
          class="mb-1"
        />
        <v-text-field
          v-model="form.password"
          label="密码"
          type="password"
          prepend-inner-icon="mdi-lock-outline"
          autocomplete="new-password"
          :rules="[rules.required]"
          class="mb-1"
        />
        <div v-if="captchaEnabled" class="flex items-start gap-3">
          <v-text-field
            v-model="form.code"
            label="验证码"
            prepend-inner-icon="mdi-shield-key-outline"
            :rules="[rules.required]"
            class="grow"
          />
          <!-- 验证码 SVG 转 data-url 展示，避免 v-html 注入风险 -->
          <v-tooltip text="点击刷新验证码" location="top">
            <template #activator="{ props: tooltipProps }">
              <img
                v-bind="tooltipProps"
                :src="captchaDataUrl"
                alt="验证码"
                width="130"
                height="56"
                class="rounded-lg cursor-pointer"
                @click="loadCaptcha"
              />
            </template>
          </v-tooltip>
        </div>
        <v-btn
          type="submit"
          color="primary"
          block
          size="large"
          class="mt-4"
          :loading="loading"
        >
          登 录
        </v-btn>
      </v-form>
    </v-card>
  </v-main>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { http } from '@/api/request'
import type { CaptchaVO } from '@/types/api'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const loading = ref(false)
const captcha = ref<CaptchaVO>({ uuid: '', img: '' })
const captchaEnabled = ref(true)
const showError = ref(false)
const errorMessage = ref('')
const form = reactive({ username: 'admin', password: '', code: '', uuid: '' })

const rules = {
  required: (v: string): boolean | string => !!v || '必填项',
}

const captchaDataUrl = computed<string>(() =>
  `data:image/svg+xml;utf8,${encodeURIComponent(captcha.value.img)}`,
)

async function loadCaptcha(): Promise<void> {
  captcha.value = await http.get<CaptchaVO>('/auth/captcha')
  form.uuid = captcha.value.uuid
  form.code = ''
}

/** 读取登录页配置：验证码关闭时隐藏输入框并允许空 code/uuid */
async function loadConfig(): Promise<void> {
  const cfg = await http.get<{ captchaEnabled: boolean }>('/auth/login-config')
  captchaEnabled.value = cfg.captchaEnabled
  if (captchaEnabled.value) {
    await loadCaptcha()
  }
}

function fail(message: string): void {
  errorMessage.value = message
  showError.value = true
}

async function onSubmit(): Promise<void> {
  loading.value = true
  showError.value = false
  try {
    await auth.login({
      username: form.username,
      password: form.password,
      code: captchaEnabled.value ? form.code : '',
      uuid: captchaEnabled.value ? form.uuid : '',
    })
    router.push((route.query.redirect as string) ?? '/')
  } catch (e) {
    fail(e instanceof Error ? e.message : '登录失败')
    if (captchaEnabled.value) {
      await loadCaptcha()
    }
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadConfig()
})
</script>
