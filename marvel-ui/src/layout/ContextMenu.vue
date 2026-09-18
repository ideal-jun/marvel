<script lang="ts">
/** 右键菜单可选项键（对应 soybean-admin 的 DropdownKey，按本项目的动作集合定义） */
export type TabDropdownKey =
  | 'closeCurrent'
  | 'closeOther'
  | 'closeLeft'
  | 'closeRight'
  | 'closeAll'
  | 'pin'
  | 'unpin'
</script>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useTabsStore } from '@/stores/tabs'

defineOptions({
  name: 'ContextMenu',
})

interface Props {
  /** ClientX */
  x: number
  /** ClientY */
  y: number
  /** 目标页签路径（本应用以路由 path 作为页签唯一标识） */
  tabPath: string
  excludeKeys?: TabDropdownKey[]
  disabledKeys?: TabDropdownKey[]
}

const props = withDefaults(defineProps<Props>(), {
  excludeKeys: () => [],
  disabledKeys: () => [],
})

const visible = defineModel<boolean>('visible', { required: true })

const route = useRoute()
const router = useRouter()
const tabs = useTabsStore()

interface DropdownOption {
  key: TabDropdownKey
  label: string
  icon?: string
  disabled?: boolean
}

const options = computed(() => {
  const opts: DropdownOption[] = [
    {
      key: 'closeCurrent',
      label: '关闭当前',
      icon: 'mdi-close',
    },
    {
      key: 'closeOther',
      label: '关闭其他',
      icon: 'mdi-view-column',
    },
    {
      key: 'closeLeft',
      label: '关闭左侧',
      icon: 'mdi-format-horizontal-align-left',
    },
    {
      key: 'closeRight',
      label: '关闭右侧',
      icon: 'mdi-format-horizontal-align-right',
    },
    {
      key: 'closeAll',
      label: '全部关闭',
      icon: 'mdi-minus',
    },
  ]

  // 首页固定，不提供置顶/取消置顶
  if (props.tabPath !== tabs.homePath) {
    if (tabs.isRetained(props.tabPath)) {
      opts.push({
        key: 'unpin',
        label: '取消置顶',
        icon: 'mdi-pin-off-outline',
      })
    } else {
      opts.push({
        key: 'pin',
        label: '置顶',
        icon: 'mdi-pin-outline',
      })
    }
  }

  const { excludeKeys, disabledKeys } = props

  const result = opts.filter((opt) => !excludeKeys.includes(opt.key))

  disabledKeys.forEach((key) => {
    const opt = result.find((item) => item.key === key)

    if (opt) {
      opt.disabled = true
    }
  })

  return result
})

function hideDropdown(): void {
  visible.value = false
}

/** 关闭类动作返回「应跳转到的路径」（被关的是当前页时非空），据此导航 */
function navigate(next: string | null): void {
  if (next) void router.push(next)
}

const dropdownAction: Record<TabDropdownKey, () => void> = {
  closeCurrent: () => navigate(tabs.removeTab(props.tabPath, route.path)),
  closeOther: () => navigate(tabs.closeOthers(props.tabPath, route.path)),
  closeLeft: () => navigate(tabs.closeLeft(props.tabPath, route.path)),
  closeRight: () => navigate(tabs.closeRight(props.tabPath, route.path)),
  closeAll: () => navigate(tabs.closeAll(route.path)),
  pin: () => tabs.setPinned(props.tabPath, true),
  unpin: () => tabs.setPinned(props.tabPath, false),
}

function handleDropdown(optionKey: TabDropdownKey): void {
  dropdownAction[optionKey]?.()
  hideDropdown()
}
</script>

<template>
  <!-- target 传 [x, y] 坐标：Vuetify 把它当零尺寸虚拟锚点，等价于 soybean 侧
       NDropdown 的 x/y + trigger="manual"；靠近视口边缘时 overlay 自动夹紧 -->
  <VMenu v-model="visible" :target="[x, y]" location="bottom start" :offset="5">
    <VList density="compact" nav prepend-gap="12">
      <VListItem
        v-for="option in options"
        :key="option.key"
        :disabled="option.disabled"
        min-height="32"
        @click="handleDropdown(option.key)"
      >
        <template #prepend>
          <VIcon v-if="option.icon" :icon="option.icon" size="small" />
        </template>
        <VListItemTitle>{{ option.label }}</VListItemTitle>
      </VListItem>
    </VList>
  </VMenu>
</template>

<style scoped></style>
