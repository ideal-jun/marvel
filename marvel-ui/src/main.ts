import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import vuetify from './plugins/vuetify'
import { useAuthStore } from './stores/auth'
import { startSse, stopSse } from './utils/sse'
// UnoCSS 产物（映射到 uno-* 级联层，层序见 public/layers.css）
import 'virtual:uno.css'
import 'unfonts.css'
// 项目对组件库的覆盖（vuetify-overrides 层），必须在 vuetify 插件样式之后加载
import './styles/overrides.css'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(vuetify)

// SSE 长连接：已登录（本地有 token）时建立，供站内消息等实时推送；
// 登出/401 时由 Layout 的登出流程与客户端自身停止并断开
if (useAuthStore().token) {
  startSse()
}

app.mount('#app')

// 页面卸载时断开，避免重连定时器残留
window.addEventListener('beforeunload', () => stopSse())
