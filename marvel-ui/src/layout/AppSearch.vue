<template>
  <!-- 顶栏搜索：Vuetify 实验室命令面板（VCommandPalette），点击图标或 Ctrl+/ 唤起。
       搜索动态菜单中的可跳转项（menuType=C），一级目录作为分组标题，选中即导航 -->
  <v-command-palette
    v-model="open"
    v-model:search="search"
    :items="items"
    :no-data-text="noDataText"
    hotkey="ctrl+k"
    max-width="500"
    density="comfortable"
    placeholder="搜索菜单..."
    class="rounded-lg min-h-[300px] max-h-[60vh]"
  >
    <template #activator="{ props: activatorProps }">
      <v-tooltip text="搜索（Ctrl+K）" location="bottom">
        <template #activator="{ props: tipProps }">
          <v-btn
            v-bind="mergeProps(activatorProps, tipProps)"
            icon="mdi-magnify"
            variant="text"
            rounded="lg"
          />
        </template>
      </v-tooltip>
    </template>

    <!-- 输入框尾部：Esc 关闭提示 -->
    <template #input.append-inner>
      <v-kbd class="opacity-70 self-center py-1">Esc</v-kbd>
    </template>

    <!-- 条目尾部：菜单路径提示 -->
    <template #item.append="{ item }">
      <span class="text-caption opacity-70">{{ itemHint(item) }}</span>
    </template>

    <!-- 底部：键盘操作图例 -->
    <template #append>
      <div
        class="flex items-center gap-1 p-3 text-caption opacity-70 border-t border-[rgba(var(--v-theme-on-surface),0.12)]"
      >
        <v-kbd>Ctrl</v-kbd><v-kbd>K</v-kbd>
        <div class="mr-3">唤起</div>
        <v-kbd><v-icon icon="$arrowup" size="14" /></v-kbd>
        <v-kbd><v-icon icon="$arrowdown" size="14" /></v-kbd>
        <div class="mr-3">导航</div>
        <v-kbd><v-icon icon="$enter" size="14" /></v-kbd>
        <div class="mr-3">打开</div>
        <v-kbd>Esc</v-kbd>
        <div class="mr-3">关闭</div>
      </div>
    </template>
  </v-command-palette>
</template>

<script setup lang="ts">
import { computed, mergeProps, ref } from 'vue'
import { useRouter } from 'vue-router'
import { VCommandPalette } from 'vuetify/labs/VCommandPalette'
import { useAuthStore } from '@/stores/auth'
import type { MenuNode } from '@/types/api'

/** 命令面板条目（VCommandPaletteItem 类型未从包入口导出，按需声明用到的字段） */
interface PaletteItem {
  type?: 'item' | 'subheader' | 'divider'
  title: string
  subtitle?: string
  prependIcon?: string
  /** 条目尾部提示（菜单路径） */
  hint?: string
  onClick?: () => void
}

const router = useRouter()
const auth = useAuthStore()
const open = ref(false)
const search = ref('')

/** 取条目尾部提示：插槽 item 的声明类型不含自定义字段，这里做一次收窄 */
function itemHint(item: unknown): string {
  return (item as PaletteItem | null)?.hint ?? ''
}

/** 菜单树 → 面板条目：目录节点渲染为分组标题（subheader），叶子菜单为可跳转项 */
const allItems = computed<PaletteItem[]>(() => {
  const out: PaletteItem[] = []
  const walk = (nodes: MenuNode[], parentPath: string): void => {
    for (const node of nodes) {
      const path = parentPath ? `${parentPath}/${node.path}` : node.path
      const children = node.children ?? []
      if (children.length) {
        out.push({ type: 'subheader', title: node.menuName })
        walk(children, path)
        continue
      }
      if (node.menuType === 'C' && node.status === '0') {
        out.push({
          title: node.menuName,
          prependIcon: node.icon || 'mdi-circle-small',
          hint: path,
          onClick: () => void router.push(`/${path}`),
        })
      }
    }
  }
  walk(auth.menus, '')
  return out
})

/** 默认不渲染条目，输入关键词后才出结果（菜单多时省掉首屏渲染与过滤开销） */
const items = computed<PaletteItem[]>(() => (search.value.trim() ? allItems.value : []))

const noDataText = computed<string>(() => (search.value.trim() ? '无匹配菜单' : '输入关键词搜索菜单'))
</script>
