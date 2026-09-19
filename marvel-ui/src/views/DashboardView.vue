<template>
  <div class="h-full flex flex-col gap-4 overflow-y-auto pr-1">
    <v-row dense>
      <v-col v-for="card in cards" :key="card.title" cols="12" sm="6" md="3">
        <v-card class="p-5">
          <div class="flex items-center gap-4">
            <v-avatar :color="card.color" size="46" rounded="lg">
              <v-icon :icon="card.icon" size="24" />
            </v-avatar>
            <div>
              <div class="text-caption text-secondary">{{ card.title }}</div>
              <div class="text-h6 font-bold">{{ card.value }}</div>
            </div>
          </div>
        </v-card>
      </v-col>
    </v-row>

    <v-row dense>
      <v-col cols="12" md="8">
        <v-card class="p-4">
          <div class="text-h6 font-bold mb-2">近 7 日登录趋势</div>
          <EChart :option="loginOption" height="300px" />
        </v-card>
      </v-col>
      <v-col cols="12" md="4">
        <v-card class="p-4">
          <div class="text-h6 font-bold mb-2">任务执行统计</div>
          <EChart :option="jobOption" height="300px" />
        </v-card>
      </v-col>
      <v-col cols="12" md="8">
        <v-card class="p-4">
          <div class="text-h6 font-bold mb-2">近 7 日操作趋势</div>
          <EChart :option="operOption" height="300px" />
        </v-card>
      </v-col>
      <v-col cols="12" md="4">
        <v-card class="p-4">
          <div class="text-h6 font-bold mb-2">操作日志模块分布</div>
          <EChart :option="moduleOption" height="300px" />
        </v-card>
      </v-col>
    </v-row>

    <v-card class="p-6">
      <div class="text-h6 font-bold mb-2">欢迎使用 Marvel 后台管理系统</div>
      <p class="text-body-2 text-secondary">
        当前登录：<span class="text-primary font-medium">{{ auth.nickname }}</span
        >， 角色：{{ auth.roles.join('、') || '—' }}。 系统采用 Spring Boot 4
        模块化单体架构，按微服务边界拆分模块，可平滑演进至 Spring Cloud。
      </p>
    </v-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import type { EChartsOption } from 'echarts'
import EChart from '@/components/EChart.vue'
import { http } from '@/api/request'
import { useAuthStore } from '@/stores/auth'
import { useAppStore } from '@/stores/app'
import type {
  DashboardStats,
  JobStats,
  LoginTrendPoint,
  NameValue,
  OperTrendPoint,
} from '@/types/api'

const auth = useAuthStore()
const app = useAppStore()

const stats = ref<DashboardStats | null>(null)
const loginTrend = ref<LoginTrendPoint[]>([])
const operTrend = ref<OperTrendPoint[]>([])
const operModule = ref<NameValue[]>([])
const jobStats = ref<JobStats | null>(null)

const axisColor = computed<string>(() => (app.dark ? '#9CA3AF' : '#6B7280'))
const splitColor = computed<string>(() =>
  app.dark ? 'rgba(255,255,255,0.08)' : 'rgba(0,0,0,0.06)',
)

interface StatCard {
  title: string
  value: number | string
  icon: string
  color: string
}

const cards = computed<StatCard[]>(() => [
  {
    title: '用户总数',
    value: stats.value?.userCount ?? '-',
    icon: 'mdi-account-multiple-outline',
    color: 'primary',
  },
  {
    title: '在线用户',
    value: stats.value?.onlineCount ?? '-',
    icon: 'mdi-account-wifi',
    color: 'success',
  },
  {
    title: '今日登录',
    value: stats.value?.todayLoginCount ?? '-',
    icon: 'mdi-login-variant',
    color: 'warning',
  },
  {
    title: '今日操作',
    value: stats.value?.todayOperCount ?? '-',
    icon: 'mdi-clipboard-text-clock-outline',
    color: 'info',
  },
])

const loginOption = computed<EChartsOption>(() => ({
  tooltip: { trigger: 'axis' },
  legend: { data: ['成功', '失败'], textStyle: { color: axisColor.value } },
  grid: { left: 44, right: 20, top: 44, bottom: 32 },
  xAxis: {
    type: 'category',
    data: loginTrend.value.map((p) => p.date.slice(5)),
    axisLabel: { color: axisColor.value },
    axisLine: { lineStyle: { color: splitColor.value } },
  },
  yAxis: {
    type: 'value',
    minInterval: 1,
    axisLabel: { color: axisColor.value },
    splitLine: { lineStyle: { color: splitColor.value } },
  },
  series: [
    {
      name: '成功',
      type: 'line',
      smooth: true,
      symbolSize: 6,
      areaStyle: { opacity: 0.12 },
      itemStyle: { color: '#16A34A' },
      data: loginTrend.value.map((p) => p.success),
    },
    {
      name: '失败',
      type: 'line',
      smooth: true,
      symbolSize: 6,
      itemStyle: { color: '#DC2626' },
      data: loginTrend.value.map((p) => p.fail),
    },
  ],
}))

const operOption = computed<EChartsOption>(() => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 44, right: 20, top: 30, bottom: 32 },
  xAxis: {
    type: 'category',
    data: operTrend.value.map((p) => p.date.slice(5)),
    axisLabel: { color: axisColor.value },
    axisLine: { lineStyle: { color: splitColor.value } },
  },
  yAxis: {
    type: 'value',
    minInterval: 1,
    axisLabel: { color: axisColor.value },
    splitLine: { lineStyle: { color: splitColor.value } },
  },
  series: [
    {
      type: 'bar',
      barWidth: '46%',
      itemStyle: { color: '#4F46E5', borderRadius: [4, 4, 0, 0] },
      data: operTrend.value.map((p) => p.count),
    },
  ],
}))

const moduleOption = computed<EChartsOption>(() => ({
  tooltip: { trigger: 'item' },
  legend: { bottom: 0, textStyle: { color: axisColor.value } },
  series: [
    {
      type: 'pie',
      radius: ['42%', '68%'],
      center: ['50%', '44%'],
      avoidLabelOverlap: true,
      label: { color: axisColor.value },
      data: operModule.value,
    },
  ],
}))

const jobOption = computed<EChartsOption>(() => {
  const success = jobStats.value?.success ?? 0
  const fail = jobStats.value?.fail ?? 0
  return {
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, textStyle: { color: axisColor.value } },
    series: [
      {
        type: 'pie',
        radius: ['48%', '72%'],
        center: ['50%', '44%'],
        label: { show: false },
        data: [
          { name: '成功', value: success, itemStyle: { color: '#16A34A' } },
          { name: '失败', value: fail, itemStyle: { color: '#DC2626' } },
        ],
      },
    ],
  }
})

async function loadAll(): Promise<void> {
  const [s, lt, ot, om, js] = await Promise.all([
    http.get<DashboardStats>('/system/dashboard/stats'),
    http.get<LoginTrendPoint[]>('/system/dashboard/login-trend', { params: { days: 7 } }),
    http.get<OperTrendPoint[]>('/system/dashboard/oper-trend', { params: { days: 7 } }),
    http.get<NameValue[]>('/system/dashboard/oper-module', { params: { limit: 6 } }),
    http.get<JobStats>('/infra/dashboard/job-stats'),
  ])
  stats.value = s
  loginTrend.value = lt
  operTrend.value = ot
  operModule.value = om
  jobStats.value = js
}

onMounted(() => {
  void loadAll()
})
</script>
