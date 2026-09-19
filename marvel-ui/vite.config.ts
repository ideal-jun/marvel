import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vuetify from 'vite-plugin-vuetify'
import UnoCSS from 'unocss/vite'
import ViteFonts from 'unplugin-fonts/vite'

export default defineConfig({
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  optimizeDeps: {
    // 依赖扫描默认只从 index.html 出发，发现不了懒加载路由里的依赖；
    // 把全部 .vue 纳入扫描入口，静态可发现的依赖在启动时一次预构建完
    entries: ['index.html', 'src/**/*.vue'],
  },
  plugins: [
    vue(),
    // styles.configFile 指向 Vuetify SASS 设置（关闭内置 utilities，见 settings.scss）
    vuetify({ autoImport: true, styles: { configFile: 'src/styles/settings.scss' } }),
    ViteFonts({
      fontsource: {
        families: [
          {
            name: 'Roboto',
            weights: [100, 300, 400, 500, 700, 900],
            styles: ['normal', 'italic'],
          },
        ],
      },
    }),
    // UnoCSS presetWind4（Vuetify 官方集成，见 uno.config.ts 与 public/layers.css）
    UnoCSS(),
  ],
  server: {
    port: 5173,
    // 编辑器/工具原子写入会在源码目录留下 .*.tmpdir 临时目录，chokidar 监听时可能抛
    // EBUSY 且无人捕获，直接崩掉 dev server；显式忽略这些中间产物
    watch: {
      ignored: ['**/.*.tmpdir/**', '**/*.tmp'],
    },
    // vuetify autoImport 的组件依赖要等 .vue 模板转换时才被发现，扫描器看不见；
    // 启动即预热全部 .vue（含根组件 App.vue）与入口，让依赖发现在用户访问前完成，
    // 避免浏览中触发依赖预构建的 full-reload 打断 SPA 跳转
    warmup: {
      clientFiles: ['./src/**/*.vue', './src/main.ts'],
    },
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true,
        rewrite: (p) => p.replace(/^\/api/, ''),
      },
      '/uploads': 'http://localhost:8081',
    },
  },
})
