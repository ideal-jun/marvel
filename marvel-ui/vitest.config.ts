import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vitest/config'

// 独立于 vite.config.ts，避免加载 UnoCSS/Vuetify/字体等构建期插件；
// 纯逻辑测试无需这些插件，后续要挂载 .vue 组件时再按需合并。
export default defineConfig({
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    include: ['src/**/*.spec.ts', 'src/**/*.test.ts'],
  },
})
