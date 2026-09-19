import '@mdi/font/css/materialdesignicons.css'
// Vuetify 基础样式（core reset/theme 变量），经 settings.scss 编译。
// 级联层顺序由 index.html 最先加载的 public/layers.css 声明，uno 与 vuetify 样式互不冲突。
import 'vuetify/styles'
// 实验室组件（VCommandPalette 顶栏命令面板）的样式不在 vuetify/styles 聚合产物内，需单独引入；
// 该文件自带 @layer vuetify-components，与项目级联层架构一致
import 'vuetify/lib/labs/VCommandPalette/VCommandPalette.css'
// VTreeview 样式同样不在 vuetify/styles 聚合产物内，需单独引入；
// 该文件自带 @layer vuetify-components，与项目级联层架构一致
import 'vuetify/lib/components/VTreeview/VTreeviewItem.css'
import { createVuetify } from 'vuetify'
// 简体中文语言包：内置组件文案 + 校验规则消息（$vuetify.rules.*）都来自这里
import { zhHans } from 'vuetify/locale'
import { forVuetify } from '../theme/breakpoints'

/**
 * Vuetify 主题：靛蓝主色 + 中性灰辅助，含 light/dark 两套。
 * UnoCSS 侧（presetWind4 theme.colors）通过 --v-theme-* 变量映射这里的主题色，
 * 改主题色全局联动；display.thresholds 与 UnoCSS 断点共用 breakpoints.ts。
 */
export default createVuetify({
  // 未配置时 Vuetify 默认 en，规则消息与内置文案都会是英文
  locale: {
    locale: 'zhHans',
    fallback: 'en',
    messages: { zhHans },
  },
  theme: {
    // 启动时从 localStorage 恢复上次选择（由 stores/app.ts 维护写入）
    defaultTheme: localStorage.getItem('marvel-theme') === 'dark' ? 'dark' : 'light',
    themes: {
      light: {
        dark: false,
        colors: {
          // 主题色从 localStorage 恢复（主题配置抽屉可运行时切换）
          primary: localStorage.getItem('marvel-primary') ?? '#4F46E5',
          secondary: '#475569',
          accent: '#7C3AED',
          background: '#F6F7FB',
          surface: '#FFFFFF',
          success: '#16A34A',
          warning: '#D97706',
          error: '#DC2626',
          info: '#0284C7',
        },
        variables: {
          'border-opacity': 0.12,
          'high-emphasis-opacity': 0.82,
          'medium-emphasis-opacity': 0.6,
          'disabled-opacity': 0.38,
          'idle-opacity': 0.04,
          'hover-opacity': 0.04,
          'focus-opacity': 0.12,
          'selected-opacity': 0.08,
          'activated-opacity': 0.12,
          'pressed-opacity': 0.12,
          'dragged-opacity': 0.08,
        },
      },
      dark: {
        dark: true,
        colors: {
          // 暗色下主色提亮一档保证对比度；主题色与 light 共用恢复值。
          // 底色/表面用纯中性灰阶（Material 暗色标高）：不带色相偏移，
          // 配任意预设主色都不冲突，也不会出现偏蓝紫、发闷的观感
          primary: localStorage.getItem('marvel-primary') ?? '#818CF8',
          secondary: '#94A3B8',
          accent: '#A78BFA',
          background: '#121212',
          surface: '#1C1C1C',
          success: '#22C55E',
          warning: '#F59E0B',
          error: '#EF4444',
          info: '#38BDF8',
        },
        variables: {
          'border-opacity': 0.12,
          'high-emphasis-opacity': 0.82,
          'medium-emphasis-opacity': 0.6,
          'disabled-opacity': 0.38,
          'idle-opacity': 0.04,
          'hover-opacity': 0.04,
          'focus-opacity': 0.12,
          'selected-opacity': 0.08,
          'activated-opacity': 0.12,
          'pressed-opacity': 0.12,
          'dragged-opacity': 0.08,
        },
      },
    },
  },
  display: {
    mobileBreakpoint: 'md',
    thresholds: forVuetify,
  },
  defaults: {
    VBtn: { rounded: 'lg' },
    // VCard 保持 Vuetify 默认（elevated 变体自带浅阴影），不做额外覆盖
    VCard: {
      elevation: 2,
    },
    VExpansionPanels: {
      static: true,
      hover: false,
      elevation: 2,
    },
    // VExpansionPanel: {

    // },// density="compact"
    VTextField: { variant: 'outlined', density: 'compact', color: 'primary' },
    VSelect: { variant: 'outlined', density: 'compact', color: 'primary' },
    VRadio: { density: 'compact', color: 'primary' },
    VRow: { density: 'compact' },
    // v-date-input 不继承 VTextField 默认值，需单独声明保持筛选控件风格统一。
    // 显示格式统一 yyyy-MM-dd：内置 date adapter 只认命名格式不认 token 字符串，
    // 故用函数格式化（displayFormat 官方支持函数形式）
    VDateInput: {
      variant: 'outlined',
      color: 'primary',
      density: 'compact',
      placeholder: 'yyyy-MM-dd',
      displayFormat: (d: Date) =>
        `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`,
    },
    VDataTable: { rounded: 'lg' },
    VList: {
      prependGap: '10',
      indent: '24',
      VIcon: {
        size: 'small',
      },
    },
  },
})
