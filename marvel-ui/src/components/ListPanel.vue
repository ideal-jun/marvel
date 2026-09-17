<template>
  <!-- 列表卡：标题工具栏 + 默认插槽（表格）。fill=true 时占满父容器剩余高度，
       表格配 fixed-header 即形成表体内部滚动；dict 主从双栏用 fill=false 自定宽度 -->
  <v-card rounded="lg" :class="fill ? 'flex-1 min-h-0 flex flex-col' : 'min-h-0 flex flex-col'">
    <!-- 标题行加 gap 统一操作区间距：v-card-title 自身无 gap，各页面不得再手工加 margin -->
    <v-card-title flat density="comfortable" class="flex items-center gap-2">
      <span>{{ title }}</span>
      <v-spacer />
      <slot name="actions" />
    </v-card-title>
    <v-divider />
    <slot />
  </v-card>
</template>

<script setup lang="ts">
withDefaults(defineProps<{ title: string; fill?: boolean }>(), { fill: true })
</script>
