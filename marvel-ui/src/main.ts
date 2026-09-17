import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import vuetify from './plugins/vuetify'
// UnoCSS 产物（映射到 uno-* 级联层，层序见 public/layers.css）
import 'virtual:uno.css'
import 'unfonts.css'
// 项目对组件库的覆盖（vuetify-overrides 层），必须在 vuetify 插件样式之后加载
import './styles/overrides.css'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(vuetify)
app.mount('#app')
