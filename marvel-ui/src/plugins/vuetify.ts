import '@mdi/font/css/materialdesignicons.css'
// Vuetify 基础样式（core reset/theme 变量），经 settings.scss 编译。
// 级联层顺序由 index.html 最先加载的 public/layers.css 声明，uno 与 vuetify 样式互不冲突。
import 'vuetify/styles'
import { createVuetify } from 'vuetify'
import { forVuetify } from '../theme/breakpoints'

/**
 * Vuetify 主题：靛蓝主色 + 中性灰辅助，含 light/dark 两套。
 * UnoCSS 侧（presetWind4 theme.colors）通过 --v-theme-* 变量映射这里的主题色，
 * 改主题色全局联动；display.thresholds 与 UnoCSS 断点共用 breakpoints.ts。
 */
export default createVuetify({
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
      },
      dark: {
        dark: true,
        colors: {
          // 暗色下主色提亮一档保证对比度，底色用低饱和深蓝灰；主题色与 light 共用恢复值
          primary: localStorage.getItem('marvel-primary') ?? '#818CF8',
          secondary: '#94A3B8',
          accent: '#A78BFA',
          background: '#0F1117',
          surface: '#1A1D27',
          success: '#22C55E',
          warning: '#F59E0B',
          error: '#EF4444',
          info: '#38BDF8',
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
      elevation: 2
    },
    VExpansionPanels: {
      static: true,
      hover: false,
      elevation: 2
    },
    // VExpansionPanel: {
      
    // }, 
    VTextField: { variant: 'outlined', density: 'comfortable' },
    VSelect: { variant: 'outlined', density: 'comfortable' },
    // v-date-input 不继承 VTextField 默认值，需单独声明 outlined 保持筛选控件风格统一
    VDateInput: { variant: 'outlined' },
    VDataTable: { rounded: 'lg' },
    VList: {
      prependGap: "10",
      indent: "24",
      VIcon: {
        size: 'small'
      }
    }
  },
})
