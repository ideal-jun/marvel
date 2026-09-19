<script setup lang="ts">
/**
 * 树形下拉选择器：v-menu + VTreeview 组合（Vuetify 原生，观感与全站一致，不引第三方）。
 *
 * 两个关键细节（踩过坑，勿删）：
 * 1) activator 必须丢掉自带的 onClick —— v-text-field 根节点包含下方 messages 区，
 *    保留会出现「点 message 也打开菜单」；改由字段的 @click:control 精确触发。
 * 2) 字段下方预留的 messages 区会把菜单往下顶，打开时按其实际高度抵消 offset，
 *    因此不需要写死偏移量，将来给字段加 rules/错误提示也不会错位。
 *    （缩进线的列宽修复见 styles/overrides.css，与组件无关，是 Vuetify 侧的坑。）
 */
import { computed, ref } from 'vue'

/** 树节点：只需要取值/显示/子级三类字段，其余字段原样透传给 VTreeview */
export type TreeSelectItem = Record<string, unknown>

const props = withDefaults(
  defineProps<{
    /** 选中值（单选）；未选传 null */
    modelValue?: string | number | null
    /** 树形数据（嵌套 children） */
    items: TreeSelectItem[]
    itemTitle?: string
    itemValue?: string
    itemChildren?: string
    label?: string
    placeholder?: string
    disabled?: boolean
    clearable?: boolean
    density?: 'default' | 'comfortable' | 'compact'
    /** 是否默认展开全部节点 */
    openAll?: boolean
    /** 手动指定菜单偏移（px）；不传则按字段下方 messages 区高度自动抵消 */
    offset?: number
    minWidth?: number | string
    maxHeight?: number | string
  }>(),
  {
    modelValue: null,
    itemTitle: 'title',
    itemValue: 'value',
    itemChildren: 'children',
    label: undefined,
    placeholder: '请选择',
    disabled: false,
    clearable: true,
    density: 'compact',
    openAll: true,
    offset: undefined,
    minWidth: 280,
    maxHeight: 360,
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: string | number | null]
}>()

const menuOpen = ref(false)
const menuOffset = ref(0)

function findItem(list: TreeSelectItem[], id: unknown): TreeSelectItem | undefined {
  for (const item of list) {
    if (item[props.itemValue] === id) return item
    const children = item[props.itemChildren]
    if (Array.isArray(children)) {
      const hit = findItem(children as TreeSelectItem[], id)
      if (hit) return hit
    }
  }
  return undefined
}

/** 输入框回显；找不到对应项（含未选）就留空，交给 placeholder */
const selectedTitle = computed(() => {
  if (props.modelValue == null || props.modelValue === '') return ''
  const item = findItem(props.items, props.modelValue)
  return item ? String(item[props.itemTitle] ?? '') : ''
})

/**
 * 去掉 activator 自带的 onClick：v-text-field 根节点包含下方 messages 区，
 * 保留会导致点 message 也打开菜单；改由 @click:control 精确触发。
 */
function withoutClick(activatorProps: Record<string, unknown>): Record<string, unknown> {
  const { onClick: _onClick, ...rest } = activatorProps
  return rest
}

function openMenu(event?: MouseEvent): void {
  if (props.disabled) return
  if (props.offset != null) {
    menuOffset.value = props.offset
  } else {
    // 从点击事件回溯到字段根节点（不能用 ref：会与 activator 自带 ref 冲突）
    const target = (event?.currentTarget ?? event?.target) as HTMLElement | null
    const root = target?.closest('.v-input')
    menuOffset.value = -(root?.querySelector('.v-input__details')?.clientHeight ?? 0)
  }
  menuOpen.value = true
}

function onActivate(values: unknown[]): void {
  const value = values[0]
  if (value == null) return
  emit('update:modelValue', value as string | number)
  menuOpen.value = false
}

function onClear(): void {
  emit('update:modelValue', null)
}
</script>

<template>
  <v-menu
    v-model="menuOpen"
    :close-on-content-click="false"
    :disabled="disabled"
    :offset="menuOffset"
    location="bottom"
  >
    <template #activator="{ props: activatorProps }">
      <v-text-field
        v-bind="withoutClick(activatorProps)"
        :model-value="selectedTitle"
        :label="label"
        :placeholder="placeholder"
        readonly
        :disabled="disabled"
        :clearable="clearable && !disabled"
        @click:control="openMenu"
        @click:clear="onClear"
      />
    </template>
    <v-treeview
      :items="items"
      :item-title="itemTitle"
      :item-value="itemValue"
      :item-children="itemChildren"
      activatable
      mandatory
      :open-all="openAll"
      open-on-click
      :density="density"
      :indent-lines="'default'"
      color="primary"
      variant="text"
      separate-roots
      :activated="modelValue != null ? [modelValue] : []"
      @update:activated="onActivate"
    />
  </v-menu>
</template>
