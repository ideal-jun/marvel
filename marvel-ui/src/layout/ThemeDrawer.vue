<template>
  <!-- 主题配置抽屉：暗色模式开关 + 预设主题色选择。
       主题色运行时改写 --v-theme-primary，UnoCSS 颜色类与 nprogress 全局联动 -->
  <v-navigation-drawer
    :model-value="app.themeDrawer"
    location="right"
    temporary
    :width="300"
    @update:model-value="app.themeDrawer = $event"
  >
    <div class="pa-5 flex flex-col gap-6">
      <div class="flex items-center justify-between">
        <span class="text-h6 font-bold">主题配置</span>
        <v-btn icon="mdi-close" variant="text" size="small" rounded="lg" @click="app.themeDrawer = false" />
      </div>

      <!-- 暗色模式 -->
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-3">
          <v-icon :icon="app.dark ? 'mdi-weather-night' : 'mdi-weather-sunny'" size="20" />
          <span class="text-body-2">暗色模式</span>
        </div>
        <v-switch
          :model-value="app.dark"
          color="primary"
          hide-details
          density="compact"
          @update:model-value="app.setDark(!!$event)"
        />
      </div>

      <v-divider />

      <!-- 主题色色卡 -->
      <div>
        <div class="flex items-center gap-3 mb-3">
          <v-icon icon="mdi-palette-outline" size="20" />
          <span class="text-body-2">主题色</span>
        </div>
        <div class="flex flex-wrap gap-3">
          <v-tooltip v-for="c in PRIMARY_PRESETS" :key="c.value" :text="c.name" location="top">
            <template #activator="{ props }">
              <button
                v-bind="props"
                type="button"
                class="w-9 h-9 rounded-full cursor-pointer flex items-center justify-center transition-transform hover:scale-110"
                :style="{ backgroundColor: c.value }"
                :aria-label="c.name"
                @click="app.setPrimary(c.value)"
              >
                <v-icon v-if="app.primary === c.value" icon="mdi-check" size="18" color="white" />
              </button>
            </template>
          </v-tooltip>
        </div>
        <div class="text-caption text-secondary mt-3">
          主题色影响按钮、链接、激活态与进度条等全局元素。
        </div>
      </div>
    </div>
  </v-navigation-drawer>
</template>

<script setup lang="ts">
import { useAppStore } from '@/stores/app'

const app = useAppStore()

/** 预设主题色（Material Design 常用主色系） */
const PRIMARY_PRESETS = [
  { name: '靛蓝（默认）', value: '#4F46E5' },
  { name: '蓝色', value: '#2086F6' },
  { name: '青色', value: '#0FA7A7' },
  { name: '绿色', value: '#16A34A' },
  { name: '琥珀', value: '#D97706' },
  { name: '红色', value: '#DC2626' },
  { name: '紫色', value: '#7C3AED' },
  { name: '玫红', value: '#DB2777' },
]
</script>
