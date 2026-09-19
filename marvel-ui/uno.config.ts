import type { StaticRule } from 'unocss'
import { defineConfig, presetWind4, transformerDirectives } from 'unocss'
import { createThemeVariants } from 'unocss-preset-vuetify'
import * as breakpoints from './src/theme/breakpoints'

/**
 * UnoCSS 配置 —— Vuetify 官方 presetWind4 集成方案（create-vuetify 模板 vue/unocss-wind4）：
 * - presetWind4 提供 TailwindCSS v4 风格工具类，按需生成；
 * - outputToCssLayers 将 uno 产物映射到 uno-* 级联层，与 public/layers.css 的层序声明配合，
 *   uno 工具类与 Vuetify 组件样式（@layer vuetify-*）互不冲突；
 * - theme.colors 通过 Vuetify 的 --v-theme-* 变量映射主题色，改主题色工具类全局联动；
 * - Vuetify 特有工具类（text-h1..text-overline、rounded-*、elevation-*）以
 *   shortcuts/rules 复刻，写法与 Vuetify 保持一致。
 */
export default defineConfig({
  presets: [presetWind4()],
  transformers: [transformerDirectives()],
  theme: {
    breakpoint: breakpoints.forUnoCSS,
    font: {
      heading: 'Roboto, sans-serif',
      body: 'Roboto, sans-serif',
      mono: '"Roboto Mono", monospace',
    },
    colors: {
      background: 'rgb(var(--v-theme-background))',
      surface: 'rgb(var(--v-theme-surface))',
      'surface-variant': 'rgb(var(--v-theme-surface-variant))',
      primary: 'rgb(var(--v-theme-primary))',
      secondary: 'rgb(var(--v-theme-secondary))',
      success: 'rgb(var(--v-theme-success))',
      warning: 'rgb(var(--v-theme-warning))',
      error: 'rgb(var(--v-theme-error))',
      info: 'rgb(var(--v-theme-info))',
    },
  },
  variants: createThemeVariants(['light', 'dark']),
  rules: [
    /** 登录页品牌渐变背景（项目自有 utility） */
    [
      'bg-brand-gradient',
      { background: 'linear-gradient(135deg, #EEF2FF 0%, #F8FAFC 45%, #F3E8FF 100%)' },
    ],
    /* elevation-* 对齐 TailwindCSS 阴影刻度（官网方案 A）。
       官网写法为纯 var(--shadow-xs..2xl)；但 wind4 的 shadow token 是按需输出
       （无内部工具类引用时不产出定义），因此补 TailwindCSS v4 标准值作 fallback：
       有 token 时走 token，无 token 时视觉一致。 */
    ['elevation-0', { 'box-shadow': 'none' }],
    ['elevation-1', { 'box-shadow': 'var(--shadow-xs, 0 1px 2px 0 rgb(0 0 0 / 0.05))' }],
    [
      'elevation-2',
      {
        'box-shadow':
          'var(--shadow-sm, 0 1px 3px 0 rgb(0 0 0 / 0.1), 0 1px 2px -1px rgb(0 0 0 / 0.1))',
      },
    ],
    [
      'elevation-3',
      {
        'box-shadow':
          'var(--shadow-md, 0 4px 6px -1px rgb(0 0 0 / 0.1), 0 2px 4px -2px rgb(0 0 0 / 0.1))',
      },
    ],
    [
      'elevation-4',
      {
        'box-shadow':
          'var(--shadow-xl, 0 20px 25px -5px rgb(0 0 0 / 0.1), 0 8px 10px -6px rgb(0 0 0 / 0.1))',
      },
    ],
    ['elevation-5', { 'box-shadow': 'var(--shadow-2xl, 0 25px 50px -12px rgb(0 0 0 / 0.25))' }],
  ] satisfies StaticRule[],
  shortcuts: {
    /* MD2 字号（与 Vuetify 2/3 命名一致，供页面直接使用） */
    'text-h1':
      '        font-heading normal-case text-[6rem]     font-[300] leading-[1]     tracking-[-.015625em]',
    'text-h2':
      '        font-heading normal-case text-[3.75rem]  font-[300] leading-[1]     tracking-[-.0083333333em]',
    'text-h3':
      '        font-heading normal-case text-[3rem]     font-[400] leading-[1.05]  tracking-[normal]',
    'text-h4':
      '        font-heading normal-case text-[2.125rem] font-[400] leading-[1.175] tracking-[.0073529412em]',
    'text-h5':
      '        font-heading normal-case text-[1.5rem]   font-[400] leading-[1.333] tracking-[normal]',
    'text-h6':
      '        font-heading normal-case text-[1.25rem]  font-[500] leading-[1.6]   tracking-[.0125em]',
    'text-subtitle-1':
      'font-body    normal-case text-[1rem]     font-[400] leading-[1.75]  tracking-[.009375em]',
    'text-subtitle-2':
      'font-body    normal-case text-[.875rem]  font-[500] leading-[1.6]   tracking-[.0071428571em]',
    'text-body-1':
      '    font-body    normal-case text-[1rem]     font-[400] leading-[1.5]   tracking-[.03125em]',
    'text-body-2':
      '    font-body    normal-case text-[.875rem]  font-[400] leading-[1.425] tracking-[.0178571429em]',
    'text-button':
      '    font-body    uppercase   text-[.875rem]  font-[500] leading-[2.6]   tracking-[.0892857143em]',
    'text-caption':
      '   font-body    normal-case text-[.75rem]   font-[400] leading-[1.667] tracking-[.0333333333em]',
    'text-overline':
      '  font-body    uppercase   text-[.75rem]   font-[500] leading-[2.667] tracking-[.1666666667em]',

    /* Vuetify 圆角命名（供 rounded prop 及页面使用） */
    'rounded-0': 'rounded-none',
    'rounded-sm': 'rounded-[2px]',
    rounded: 'rounded-[4px]',
    'rounded-lg': 'rounded-[8px]',
    'rounded-xl': 'rounded-[24px]',
    'rounded-pill': 'rounded-full',
    'rounded-circle': 'rounded-[50%]',

    'elevation-0': 'shadow-none',
    'elevation-1': 'shadow-xs',
    'elevation-2': 'shadow-sm',
    'elevation-3': 'shadow-md',
    'elevation-4': 'shadow-xl',
    'elevation-5': 'shadow-2xl',
  },
  safelist: [
    // shortcuts 中 font-* 组合类
    'font-heading',
    'font-body',
    'font-mono',
    // Vuetify elevation prop 动态生成的类
    ...Array.from({ length: 6 }, (_, i) => `elevation-${i}`),
    // Vuetify rounded prop 动态生成的类
    ...['', '-0', '-sm', '-lg', '-xl', '-pill', '-circle', '-shaped'].map(
      (suffix) => `rounded${suffix}`,
    ),
    // 组件 color prop 动态绑定的主题色类（Dashboard 统计卡 / 状态 chip 等）
    'bg-primary',
    'text-primary',
    'bg-secondary',
    'text-secondary',
    'bg-success',
    'text-success',
    'bg-warning',
    'text-warning',
    'bg-error',
    'text-error',
    'bg-info',
    'text-info',
  ],
  outputToCssLayers: {
    cssLayerName: (layer) => (layer === 'properties' ? null : `uno-${layer}`),
  },
})
