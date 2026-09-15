<template>
  <!-- 页签栏（Chrome 风格）：置于 v-app-bar 的 extension 区。
       左=页签（点击切换/关闭），右=刷新与内容全屏。
       形状样式见下方 scoped：激活页签上圆角 + 下角内凹弧线与内容区连为一体 -->
  <div class="chrome-tabs h-full w-full flex items-end">
    <v-tabs
      :model-value="route.path"
      density="compact"
      class="min-w-0 flex-1 chrome-tabs__bar"
      :show-arrows="false"
    >
      <v-tab
        v-for="t in tabs.visited"
        :key="t.path"
        :value="t.path"
        :to="t.path"
        slim
        class="chrome-tab text-none group"
      >
        <v-icon v-if="t.icon" :icon="t.icon" size="15" class="mr-1" />
        <span class="text-body-2">{{ t.title }}</span>
        <v-icon
          v-if="!t.affix"
          icon="mdi-close"
          size="14"
          class="ml-1 opacity-40 group-hover:opacity-100 transition-opacity"
          @click.prevent.stop="onClose(t.path)"
        />
      </v-tab>
    </v-tabs>
    <v-spacer />
    <div class="self-center flex items-center pr-2">
      <v-tooltip text="刷新当前页" location="bottom">
        <template #activator="{ props }">
          <v-btn v-bind="props" icon="mdi-refresh" variant="text" size="small" rounded="lg" @click="app.reload()" />
        </template>
      </v-tooltip>
      <v-tooltip :text="app.contentFullscreen ? '退出内容全屏' : '内容全屏'" location="bottom">
        <template #activator="{ props }">
          <v-btn
            v-bind="props"
            :icon="app.contentFullscreen ? 'mdi-fullscreen-exit' : 'mdi-fullscreen'"
            variant="text"
            size="small"
            rounded="lg"
            @click="app.contentFullscreen = !app.contentFullscreen"
          />
        </template>
      </v-tooltip>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { useAppStore } from '@/stores/app'
import { useTabsStore } from '@/stores/tabs'

const route = useRoute()
const router = useRouter()
const app = useAppStore()
const tabs = useTabsStore()

/** 关闭页签：关闭当前页时跳转到相邻页签 */
function onClose(path: string): void {
  const next = tabs.removeTab(path, route.path)
  if (next) void router.push(next)
}
</script>

<style scoped>
/* 隐藏 Vuetify 默认激活下划线（Chrome 页签无下划线，靠背景上浮区分） */
:deep(.chrome-tab .v-tab__slider) {
  display: none;
}

/* Chrome 风格页签：栏底保持表面色（与顶栏一体），激活页签用主题色。
   颜色全部走 --v-theme-* 变量，明暗主题自动适配 */
.chrome-tabs {
  background: rgb(var(--v-theme-surface));
}

/* 页签底端对齐栏底，高度略小于栏高留出呼吸感 */
:deep(.chrome-tabs__bar) {
  height: 100%;
}

:deep(.chrome-tab) {
  position: relative;
  height: 32px;
  margin-top: 8px;
  border-radius: 10px 10px 0 0;
  letter-spacing: normal;
  min-width: 90px;
  transition: background 0.15s ease;
}

/* 非激活页签：悬停浮灰 */
:deep(.chrome-tab:not(.v-tab--selected):hover) {
  background: rgba(var(--v-theme-on-surface), 0.06);
}

/* 激活页签：主题色块，文字图标用反色（on-primary，明暗主题自动适配）。
   position:relative 必须显式声明——VBtn 默认无定位，z-index 不生效会导致
   左右外扩的内凹角被相邻标签按文档序盖住（右侧尤其明显） */
:deep(.chrome-tab.v-tab--selected) {
  position: relative;
  z-index: 2;
  background: rgb(var(--v-theme-primary));
  color: rgb(var(--v-theme-on-primary));
}

/* Chrome 标志性内凹角：激活页签左右下角用径向渐变补出反向圆弧。
   注意 VBtn 的伪元素被 Vuetify 占用：::before 挂 hover 底色（currentColor）、
   ::after 是 focus 轮廓层（2px currentColor 边框 + inset 铺满），
   两者都必须显式清掉，否则弧线上叠着深色块/边框 */
:deep(.chrome-tab.v-tab--selected)::before,
:deep(.chrome-tab.v-tab--selected)::after {
  content: '';
  position: absolute;
  top: auto;
  bottom: 0;
  left: auto;
  right: auto;
  width: 10px;
  height: 10px;
  opacity: 1;
  border: none;
  border-radius: 0;
  background-color: transparent;
  pointer-events: none;
}

:deep(.chrome-tab.v-tab--selected)::before {
  left: -10px;
  background: radial-gradient(circle 10px at 0 0, transparent 98%, rgb(var(--v-theme-primary)) 100%);
}

:deep(.chrome-tab.v-tab--selected)::after {
  right: -10px;
  background: radial-gradient(circle 10px at 100% 0, transparent 98%, rgb(var(--v-theme-primary)) 100%);
}
</style>
