<template>
  <!-- 页签栏：ChromeTab 移植自 soybean-admin（SVG 几何轮廓 + 负边距重叠 + 接缝分隔线），
       按本项目细节调整：主题色走 --v-theme-primary（color-mix 派生明暗两套激活底色）。
       交互为 soybean 同款：点击切换/中键或 × 关闭/右键上下文菜单/滚轮横滚/激活自动居中；
       右侧=刷新（带 loading）、批量关闭下拉、内容全屏 -->
  <div class="tab-bar h-full w-full flex items-end">
    <!-- 滚动区：滚轮竖向滚动转横向；隐藏滚动条，激活页签自动滚动居中 -->
    <div ref="scrollRef" class="tab-scroll min-w-0 flex-1 h-full" @wheel="onWheel">
      <div class="tab-track" role="tablist">
        <div
          v-for="t in tabs.visited"
          :key="t.path"
          role="tab"
          tabindex="0"
          :aria-selected="t.path === route.path"
          :title="t.title"
          class="chrome-tab"
          :class="{
            'chrome-tab--active': t.path === route.path,
            'chrome-tab--dark': app.dark,
          }"
          @click="onSwitch(t.path)"
          @keydown.enter.prevent="onSwitch(t.path)"
          @mousedown="onMousedown($event, t)"
          @contextmenu.prevent="onContextMenu($event, t)"
        >
          <!-- 页签轮廓背景：currentColor 填充，配色见下方样式 -->
          <div class="chrome-tab__bg">
            <ChromeTabBg />
          </div>
          <v-icon v-if="t.icon" :icon="t.icon" size="16" class="shrink-0" />
          <span class="chrome-tab__label">{{ t.title }}</span>
          <!-- 置顶页签以图钉标识且不可关闭；首页无任何角标 -->
          <span v-if="t.pinned" class="chrome-tab__pin shrink-0">
            <v-icon icon="mdi-pin" size="13" />
          </span>
          <span
            v-else-if="!t.affix"
            class="chrome-tab__close shrink-0"
            @click.stop.prevent="closeBy('current', t.path)"
          >
            <v-icon icon="mdi-close" size="12" />
          </span>
          <!-- 接缝分隔线：悬停/激活时隐藏（下一页签抬升后覆盖） -->
          <div class="chrome-tab-divider" />
        </div>
      </div>
    </div>

    <!-- 右侧操作区：刷新 / 批量关闭 / 内容全屏 -->
    <div class="self-center flex items-center pr-2 shrink-0">
      <v-tooltip text="刷新当前页" location="bottom">
        <template #activator="{ props }">
          <v-btn
            v-bind="props"
            icon="mdi-refresh"
            variant="text"
            size="small"
            rounded="lg"
            :loading="refreshing"
            @click="onRefresh"
          />
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

    <!-- 右键上下文菜单（同 soybean-admin 的 ContextMenu：光标坐标 + disabledKeys 下传） -->
    <ContextMenu
      v-model:visible="ctx.visible"
      :x="ctx.x"
      :y="ctx.y"
      :tab-path="ctx.path"
      :disabled-keys="ctxDisabled"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAppStore } from '@/stores/app'
import { useTabsStore } from '@/stores/tabs'
import type { TabItem } from '@/stores/tabs'
import ChromeTabBg from './ChromeTabBg.vue'
import ContextMenu from './ContextMenu.vue'
import type { TabDropdownKey } from './ContextMenu.vue'

const route = useRoute()
const router = useRouter()
const app = useAppStore()
const tabs = useTabsStore()

/* ---------- 切换 / 关闭 ---------- */

function onSwitch(path: string): void {
  if (path !== route.path) void router.push(path)
}

/** 统一的关闭入口：store 返回应跳转的路径（当前页被关时非空） */
function closeBy(kind: 'current' | 'others' | 'left' | 'right', path: string): void {
  const cur = route.path
  const next =
    kind === 'current' ? tabs.removeTab(path, cur)
    : kind === 'others' ? tabs.closeOthers(path, cur)
    : kind === 'left' ? tabs.closeLeft(path, cur)
    : tabs.closeRight(path, cur)
  if (next) void router.push(next)
}

function onCloseAll(): void {
  const next = tabs.closeAll(route.path)
  if (next) void router.push(next)
}

/** 中键关闭（浏览器新标签页习惯，同 soybean-admin） */
function onMousedown(e: MouseEvent, tab: TabItem): void {
  if (e.button !== 1 || tabs.isRetained(tab.path)) return
  e.preventDefault()
  closeBy('current', tab.path)
}

/* ---------- 右键上下文菜单 ---------- */

const ctx = reactive({ visible: false, x: 0, y: 0, path: '' })

/** 右键：记录光标坐标与目标页签，其余交给 ContextMenu 组件（定位/夹紧/关闭均由 overlay 处理） */
function onContextMenu(e: MouseEvent, tab: TabItem): void {
  ctx.x = e.clientX
  ctx.y = e.clientY
  ctx.path = tab.path
  ctx.visible = true
}

/** 关闭项禁用键（右键菜单的 disabledKeys 与右侧下拉共用）：
    保留页签（首页/置顶）不可关；左右无普通页签时对应项禁用 */
function getDisabledKeys(path: string): TabDropdownKey[] {
  const idx = tabs.visited.findIndex((t) => t.path === path)
  if (idx < 0) return ['closeCurrent', 'closeOther', 'closeLeft', 'closeRight', 'closeAll']
  const closable = (t: TabItem) => !t.affix && !t.pinned
  const left = tabs.visited.slice(0, idx).some(closable)
  const right = tabs.visited.slice(idx + 1).some(closable)
  const keys: TabDropdownKey[] = []
  if (tabs.isRetained(path)) keys.push('closeCurrent')
  if (!left && !right) keys.push('closeOther')
  if (!left) keys.push('closeLeft')
  if (!right) keys.push('closeRight')
  if (!left && !right && tabs.isRetained(path)) keys.push('closeAll')
  return keys
}

const ctxDisabled = computed(() => getDisabledKeys(ctx.path))
const barDisabled = computed(() => getDisabledKeys(route.path))

/* ---------- 刷新（带短暂 loading，反馈已触发） ---------- */

const refreshing = ref(false)

function onRefresh(): void {
  if (refreshing.value) return
  refreshing.value = true
  app.reload()
  window.setTimeout(() => {
    refreshing.value = false
  }, 500)
}

/* ---------- 滚轮横向滚动 + 激活页签自动居中 ---------- */

const scrollRef = ref<HTMLElement>()

/** 竖向滚轮转横向滚动页签；无横向溢出时不拦截，保持页面原生滚动 */
function onWheel(e: WheelEvent): void {
  const el = scrollRef.value
  if (!el || el.scrollWidth <= el.clientWidth) return
  e.preventDefault()
  el.scrollLeft += e.deltaY
}

async function scrollToActive(): Promise<void> {
  await nextTick()
  scrollRef.value
    ?.querySelector('.chrome-tab--active')
    ?.scrollIntoView({ behavior: 'smooth', inline: 'center', block: 'nearest' })
}

// 路由切换或置顶数变化（固定区重排会移动激活页签）后，把激活页签滚到可视区中央
watch(
  () => [route.path, tabs.fixedCount] as const,
  () => void scrollToActive(),
  { immediate: true },
)
</script>

<style scoped>
.tab-bar {
  background: rgb(var(--v-theme-surface));
}

/* 滚动区隐藏滚动条（Chrome 页签条无滚动条形态）。
   isolation 建立层叠上下文：页签轮廓背景是 z-index:-1 的负值子元素，
   必须限制在本容器内绘制，否则会被 tab-bar 的表面背景盖住 */
.tab-scroll {
  overflow-x: auto;
  scrollbar-width: none;
  isolation: isolate;
}
.tab-scroll::-webkit-scrollbar {
  display: none;
}

.tab-track {
  display: flex;
  align-items: flex-end;
  width: max-content;
  height: 100%;
  /* 左对齐 soybean 的 px-16px；右侧补偿末页签的 -18px 负边距 */
  padding: 0 18px 0 16px;
}

/* ---- ChromeTab（移植自 soybean-admin，尺寸按实测对齐：
   条高 44 / 页签高≈33（6px 上下内距 + 21px 内容行）/ 内距 24px / 元素间距 16px）----
   页签间 -18px 负边距重叠，形成 Chrome 相邻页签咬合的轮廓；
   z-index：默认 0 < 悬停 9 < 激活 10，抬升者背景覆盖相邻页签接缝 */
.chrome-tab {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 16px;
  flex-shrink: 0;
  margin-right: -18px;
  padding: 6px 24px;
  cursor: pointer;
  user-select: none;
  white-space: nowrap;
  transition: color 0.3s;
}

.chrome-tab:hover {
  z-index: 9;
}
.chrome-tab--active {
  z-index: 10;
  color: rgb(var(--v-theme-primary));
}

/* 轮廓背景：SVG fill=currentColor。非激活时透明，
   悬停浮浅灰（soybean 配方 #dee1e6 / 暗色 #333）。
   颜色必须瞬时切换、不可加过渡：扫过页签时 z-index 瞬时降落，
   渐隐中的灰底右缘会被相邻页签盒子切断，视觉上闪一条竖线 */
.chrome-tab__bg {
  position: absolute;
  inset: 0;
  z-index: -1;
  pointer-events: none;
  color: transparent;
}
.chrome-tab:hover .chrome-tab__bg {
  color: #dee1e6;
}
.chrome-tab--dark:hover .chrome-tab__bg {
  color: #333333;
}

/* 激活底色：主题色与白/黑混合（soybean 的 primary1/primary2 配方），
   主题色变化经 --v-theme-primary 运行时联动 */
.chrome-tab--active .chrome-tab__bg,
.chrome-tab--active:hover .chrome-tab__bg {
  color: color-mix(in srgb, rgb(var(--v-theme-primary)) 10%, #ffffff);
}
.chrome-tab--active.chrome-tab--dark .chrome-tab__bg,
.chrome-tab--active.chrome-tab--dark:hover .chrome-tab__bg {
  color: color-mix(in srgb, rgb(var(--v-theme-primary)) 30%, #000000);
}

.chrome-tab__label {
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 0.875rem;
  /* 行高撑起 21px 内容高，与 soybean 页签总高 33px（+上下 6px 内距）对齐 */
  line-height: 21px;
}

/* 接缝分隔线：默认可见，悬停/激活时隐藏（soybean 配方 #1f2225 / 暗色白 90%）。
   隐藏必须瞬时（无过渡）：若淡出，深色线会叠在渐显的悬停灰底上，视觉上闪竖线 */
.chrome-tab-divider {
  position: absolute;
  right: 7px;
  height: 16px;
  width: 1px;
  background: #1f2225;
  pointer-events: none;
}
.chrome-tab--dark .chrome-tab-divider {
  background: rgba(255, 255, 255, 0.9);
}
.chrome-tab:hover .chrome-tab-divider,
.chrome-tab--active .chrome-tab-divider {
  opacity: 0;
}

/* 关闭按钮：16px 圆形热区，默认全显不透明（soybean 无降透明处理）；
   悬停自身时呈圆形底——非激活灰底白叉、激活主题色底（暗色下叉为黑） */
.chrome-tab__close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  transition: background-color 0.15s ease, color 0.15s ease;
}
.chrome-tab__close:hover {
  background: #9ca3af;
  color: #ffffff;
}
.chrome-tab--dark .chrome-tab__close:hover {
  color: #000000;
}
.chrome-tab--active .chrome-tab__close:hover {
  background: rgb(var(--v-theme-primary));
  color: #ffffff;
}
.chrome-tab--active.chrome-tab--dark .chrome-tab__close:hover {
  color: #000000;
}

/* 置顶图钉角标：随页签文字颜色，激活页签上即为主题色 */
.chrome-tab__pin {
  display: flex;
  align-items: center;
  opacity: 0.7;
}
</style>
