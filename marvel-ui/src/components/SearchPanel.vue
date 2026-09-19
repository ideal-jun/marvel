<template>
  <!-- 列表页搜索卡：组件库折叠面板 v-expansion-panels，默认展开。
       默认插槽放 v-col 搜索字段（外层已提供 v-row），按钮区固定在底部 -->
  <v-expansion-panels v-model="opened" rounded="lg">
    <v-expansion-panel value="search">
      <v-expansion-panel-title expand-icon="mdi-chevron-up" collapse-icon="mdi-chevron-down">
        搜索条件
      </v-expansion-panel-title>
      <v-expansion-panel-text>
        <v-row>
          <slot />
          <v-col cols="12" class="text-right">
            <v-btn color="primary" prepend-icon="mdi-magnify" @click="emit('search')">搜索</v-btn>
            <v-btn class="ml-3" prepend-icon="mdi-refresh" @click="emit('reset')">重置</v-btn>
          </v-col>
        </v-row>
      </v-expansion-panel-text>
    </v-expansion-panel>
  </v-expansion-panels>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const emit = defineEmits<{ search: []; reset: [] }>()

/** 默认展开；单面板模式的 v-model 是所选项的数组 */
const opened = ref<string[]>(['search'])
</script>
