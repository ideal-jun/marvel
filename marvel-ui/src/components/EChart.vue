<template>
  <div ref="el" class="w-full" :style="{ height }" />
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import type { EChartsOption } from 'echarts'
import { useAppStore } from '@/stores/app'

/**
 * ECharts 通用容器：负责实例生命周期、尺寸自适应与明暗主题重绘。
 * 具体图表配置由父组件通过 option 传入。
 */
const props = withDefaults(defineProps<{ option: EChartsOption; height?: string }>(), {
  height: '320px',
})

const app = useAppStore()
const el = ref<HTMLElement | null>(null)
let chart: echarts.ECharts | null = null

function render(): void {
  if (!el.value) return
  if (!chart) chart = echarts.init(el.value)
  chart.setOption(props.option, true)
}

function resize(): void {
  chart?.resize()
}

onMounted(() => {
  render()
  window.addEventListener('resize', resize)
})

// 明暗主题切换或数据变化时重绘（notMerge=true 避免残留旧系列）
watch(() => app.dark, render)
watch(() => props.option, render, { deep: true })

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  chart?.dispose()
  chart = null
})
</script>
