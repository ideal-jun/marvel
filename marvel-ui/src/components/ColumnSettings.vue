<template>
  <!-- 列设置弹层：列显隐（复选框）、固定到左侧（图钉）、拖拽排序（把手）。
       通过 v-model:columns 双向绑定列定义数组（页面据此计算表格 headers） -->
  <v-menu location="bottom end" :width="280" :close-on-content-click="false">
    <template #activator="{ props: menuProps }">
      <v-tooltip text="列设置" location="bottom">
        <template #activator="{ props: tipProps }">
          <v-btn v-bind="mergeProps(menuProps, tipProps)" icon="mdi-table-cog" variant="text" density="comfortable" rounded="lg" />
        </template>
      </v-tooltip>
    </template>

    <v-card rounded="lg" class="mt-2">
      <div class="flex items-center justify-between pl-2 pr-3 py-1">
        <v-checkbox
          :model-value="allVisible"
          :indeterminate="someVisible && !allVisible"
          label="列显示"
          density="compact"
          hide-details
          class="flex-none"
          @update:model-value="toggleAll"
        />
        <span class="text-caption text-secondary">拖拽调整顺序</span>
      </div>
      <v-divider />

      <!-- 原生 HTML5 拖拽排序：dragstart 记录起点，drop 时移动数组元素 -->
      <v-list density="compact" class="py-0 overflow-y-auto" max-height="360">
        <v-list-item
          v-for="(c, i) in columns"
          :key="c.key"
          draggable
          class="cursor-grab"
          @dragstart="dragIndex = i"
          @dragover.prevent
          @drop="onDrop(i)"
        >
          <template #prepend>
            <v-icon icon="mdi-drag-vertical" size="16" class="text-secondary" />
          </template>
          <v-checkbox
            v-model="c.visible"
            :label="c.title"
            density="compact"
            hide-details
          />
          <template #append>
            <!-- 右侧固定（操作列）由页面声明，不提供取消 -->
            <v-tooltip
              :text="isEndFixed(c) ? '已固定到右侧' : c.fixed ? '取消固定' : '固定到左侧'"
              location="start"
            >
              <template #activator="{ props }">
                <v-btn
                  v-bind="props"
                  icon
                  size="x-small"
                  variant="text"
                  :disabled="isEndFixed(c)"
                  @click.stop="toggleFixed(c)"
                >
                  <v-icon
                    :icon="c.fixed ? 'mdi-pin' : 'mdi-pin-outline'"
                    :color="c.fixed ? 'primary' : undefined"
                    size="16"
                  />
                </v-btn>
              </template>
            </v-tooltip>
          </template>
        </v-list-item>
      </v-list>
    </v-card>
  </v-menu>
</template>

<script lang="ts">
/** 列定义：页面把静态 headers 改造为该结构的响应式数组 */
export interface ColumnDef {
  key: string
  title: string
  width?: number | string
  sortable?: boolean
  visible: boolean
  /** true/'start'=固定到左侧；'end'=固定到右侧（操作列，列设置不可取消） */
  fixed?: boolean | 'start' | 'end'
}
</script>

<script setup lang="ts">
import { computed, mergeProps, ref } from 'vue'

const columns = defineModel<ColumnDef[]>('columns', { required: true })

/** 正在拖拽的列下标 */
const dragIndex = ref<number | null>(null)

const allVisible = computed<boolean>(() => columns.value.every((c) => c.visible))
const someVisible = computed<boolean>(() => columns.value.some((c) => c.visible))

function toggleAll(v: boolean | null): void {
  const visible = v ?? false
  columns.value.forEach((c) => {
    c.visible = visible
  })
}

/** 右侧固定（操作列）：由页面声明，不提供取消 */
function isEndFixed(c: ColumnDef): boolean {
  return c.fixed === 'end'
}

function toggleFixed(c: ColumnDef): void {
  if (isEndFixed(c)) return
  // Vuetify 固定列必须有静态 width，否则布局计算异常
  if (!c.width && !c.fixed) return
  c.fixed = !c.fixed
}

/** 拖拽落点：把起点列移动到目标位置 */
function onDrop(target: number): void {
  const from = dragIndex.value
  dragIndex.value = null
  if (from === null || from === target) return
  const [moved] = columns.value.splice(from, 1)
  columns.value.splice(target, 0, moved)
}
</script>
