<template>
  <!-- 注意：v-app 只在 App.vue 根组件出现一次；本组件作为布局层
       直接提供 v-navigation-drawer / v-app-bar / v-main，
       再嵌套 v-app 会形成双重布局坐标系，导致整页随内容滚动。
       内容全屏（TagsBar 右侧按钮）时隐藏侧栏与顶栏，仅保留内容区 -->
  <v-navigation-drawer v-if="!app.contentFullscreen">
      <template #prepend>
      <div class="flex items-center gap-2 p-4">
        <v-avatar color="primary" size="34" rounded="lg">
          <v-icon icon="mdi-hexagon-multiple" size="20" />
        </v-avatar>
        <span class="text-h6 font-bold">Marvel Admin</span>
      </div>
      <v-divider />
    </template>

      <!-- color prop 定制激活态颜色（Vuetify 4：active-color 已弃用），无需覆盖底层样式 -->
      <v-list nav density="comfortable" color="primary">
        <!-- exact 必须加：首页是 layout 的空 path 默认子路由（'' 解析后与父级 '/' 同路径），
             vue-router 对默认子路由会退而匹配父级 record，导致任何子页面下首页都被判激活 -->
        <v-list-item
          prepend-icon="mdi-view-dashboard-outline"
          title="首页"
          :to="{ name: 'dashboard' }"
          exact
          rounded="lg"
        />
        <template v-for="menu in auth.menus" :key="menu.id">
          <v-list-group :value="menu.menuName">
            <template #activator="{ props }">
              <v-list-item
                v-bind="props"
                :prepend-icon="menu.icon || 'mdi-menu'"
                :title="menu.menuName"
                rounded="lg"
              />
            </template>
            <v-list-item
              v-for="child in menu.children ?? []"
              :key="child.id"
              :title="child.menuName"
              :prepend-icon="child.icon || 'mdi-circle-small'"
              :to="{ path: `/${menu.path}/${child.path}` }"
              rounded="lg"
            />
          </v-list-group>
        </template>
      </v-list>

      <template #append>
        <div class="text-caption text-center p-4 opacity-60">v1.0.0 · Modular Monolith</div>
      </template>
    </v-navigation-drawer>

    <!-- 边框色用 on-surface 主题变量的低透明度，明暗主题自适应 -->
    <v-app-bar v-if="!app.contentFullscreen" flat class="border-b border-[rgba(var(--v-theme-on-surface),0.12)]">
      <v-spacer />
      <!-- 功能按钮组：站内消息 / 菜单搜索 / 浏览器全屏 / 主题切换 -->
      <NoticeCenter />
      <AppSearch />
      <v-tooltip text="全屏" location="bottom">
        <template #activator="{ props }">
          <v-btn
            v-bind="props"
            :icon="fullscreen ? 'mdi-fullscreen-exit' : 'mdi-fullscreen'"
            variant="text"
            rounded="lg"
            @click="toggleFullscreen"
          />
        </template>
      </v-tooltip>
      <v-tooltip :text="app.dark ? '切换亮色模式' : '切换暗色模式'" location="bottom">
        <template #activator="{ props }">
          <v-btn
            v-bind="props"
            :icon="app.dark ? 'mdi-weather-night' : 'mdi-weather-sunny'"
            variant="text"
            rounded="lg"
            @click="app.toggleTheme()"
          />
        </template>
      </v-tooltip>
      <v-tooltip text="主题配置" location="bottom">
        <template #activator="{ props }">
          <v-btn v-bind="props" icon="mdi-palette" variant="text" rounded="lg" @click="app.themeDrawer = true" />
        </template>
      </v-tooltip>
      <v-divider vertical inset class="mx-2" />
      <v-menu>
        <template #activator="{ props: menuProps }">
          <v-btn v-bind="menuProps" variant="text" rounded="lg" class="text-none mr-2">
            <v-avatar color="primary" size="28" class="mr-2">
              <span class="text-caption font-weight-bold">{{ avatarText }}</span>
            </v-avatar>
            <span class="text-body-2">{{ auth.nickname }}</span>
            <v-icon icon="mdi-chevron-down" size="18" class="ml-1" />
          </v-btn>
        </template>
        <v-list nav density="compact" elevation="4">
          <v-list-item title="退出登录" prepend-icon="mdi-logout" @click="onLogout" />
        </v-list>
      </v-menu>

      <!-- 页签栏挂在 app-bar 的 extension 区，v-main 会自动为其留出偏移 -->
      <template #extension>
        <TagsBar />
      </template>
    </v-app-bar>

    <!-- scrollable：v-main 绝对定位铺满可视区（自动避开 app-bar/drawer），
         内容在 main 区域内部滚动而非整页滚动；配合容器 h-full 可做满高页面 -->
    <v-main scrollable>
      <v-container fluid class="h-full flex flex-col">
        <!-- 内容全屏时顶栏已隐藏，提供悬浮退出入口避免被困 -->
        <v-fade-transition>
          <v-btn
            v-if="app.contentFullscreen"
            icon="mdi-fullscreen-exit"
            color="primary"
            class="fixed top-4 right-4 z-20"
            rounded="lg"
            elevation="4"
            @click="app.contentFullscreen = false"
          />
        </v-fade-transition>
        <router-view v-slot="{ Component, route }">
          <v-scroll-x-transition mode="out-in">
            <!-- key 拼接 reloadKey：TagsBar 的"刷新当前页"自增后重建页面组件 -->
            <div :key="`${route.path}-${app.reloadKey}`" class="flex-1 min-h-0 flex flex-col">
              <component :is="Component" />
            </div>
          </v-scroll-x-transition>
        </router-view>
      </v-container>
    </v-main>
    <!-- 主题配置抽屉（右侧临时抽屉，不参与布局偏移） -->
    <ThemeDrawer />
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useAppStore } from '@/stores/app'
import { useTabsStore } from '@/stores/tabs'
import AppSearch from '@/layout/AppSearch.vue'
import TagsBar from '@/layout/TagsBar.vue'
import ThemeDrawer from '@/layout/ThemeDrawer.vue'
import NoticeCenter from '@/layout/NoticeCenter.vue'

const auth = useAuthStore()
const app = useAppStore()
const tabs = useTabsStore()
const route = useRoute()
const router = useRouter()

/** 头像取昵称首字符 */
const avatarText = computed<string>(() => auth.nickname.slice(0, 1) || 'U')

/* ---------- 浏览器全屏（区别于内容区全屏） ---------- */
const fullscreen = ref(false)

function toggleFullscreen(): void {
  if (document.fullscreenElement) {
    void document.exitFullscreen()
  } else {
    void document.documentElement.requestFullscreen()
  }
}

function onFsChange(): void {
  fullscreen.value = !!document.fullscreenElement
}

onMounted(() => document.addEventListener('fullscreenchange', onFsChange))
onUnmounted(() => document.removeEventListener('fullscreenchange', onFsChange))

/* ---------- 页签：路由变化即收录 ---------- */
watch(
  () => route.path,
  () => tabs.addTab(route),
  { immediate: true },
)

async function onLogout(): Promise<void> {
  await auth.logout()
  tabs.reset()
  router.push('/login')
}
</script>
