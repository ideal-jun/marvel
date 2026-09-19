import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import NProgress from 'nprogress'
import { useAuthStore } from '@/stores/auth'
import type { MenuNode } from '@/types/api'

// 路由切换顶部进度条；隐藏右上角自带 spinner，只保留顶条
import 'nprogress/nprogress.css'
import '@/styles/nprogress.css'

NProgress.configure({ showSpinner: false, speed: 300, minimum: 0.2 })

const LoginView = (): Promise<typeof import('@/views/LoginView.vue')> =>
  import('@/views/LoginView.vue')
const Layout = (): Promise<typeof import('@/layout/Layout.vue')> => import('@/layout/Layout.vue')
const DashboardView = (): Promise<typeof import('@/views/DashboardView.vue')> =>
  import('@/views/DashboardView.vue')
const ProfileView = (): Promise<typeof import('@/views/profile/index.vue')> =>
  import('@/views/profile/index.vue')

// component 字符串到动态导入的映射（对应后端菜单表 component 字段）
const viewModules = import.meta.glob('../views/**/*.vue')

/** 将后端菜单树展开为「layout 子路由」记录，目录层级拼入完整路径（如 /system/user） */
export function toRouteRecords(menus: MenuNode[], parentPath = ''): RouteRecordRaw[] {
  const records: RouteRecordRaw[] = []
  for (const m of menus) {
    const fullPath = m.path ? `${parentPath}/${m.path}` : parentPath
    if (m.menuType === 'C' && m.component) {
      records.push({
        path: fullPath,
        name: `menu-${m.id}`,
        component: viewModules[`../views/${m.component}.vue`],
        meta: { title: m.menuName, icon: m.icon },
      })
    }
    if (m.children?.length) {
      records.push(...toRouteRecords(m.children, fullPath))
    }
  }
  return records
}

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: LoginView, meta: { public: true } },
    {
      path: '/',
      name: 'layout',
      component: Layout,
      children: [
        {
          path: '',
          name: 'dashboard',
          component: DashboardView,
          meta: { title: '首页', icon: 'mdi-view-dashboard-outline' },
        },
        // 个人中心为登录用户通用页面，不走后端菜单授权（入口在顶栏用户下拉）
        {
          path: 'profile',
          name: 'profile',
          component: ProfileView,
          meta: { title: '个人中心', icon: 'mdi-account-circle-outline' },
        },
      ],
    },
    // 未匹配路由兜底：由守卫在动态路由注册完成后再决定去向
    { path: '/:pathMatch(.*)*', name: 'not-found', component: Layout, children: [] },
  ],
})

router.beforeEach(() => {
  NProgress.start()
  return true
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (to.meta.public) return true
  if (!auth.isLoggedIn) {
    return { path: '/login', query: to.fullPath !== '/' ? { redirect: to.fullPath } : {} }
  }
  if (!auth.menus.length) {
    try {
      await auth.fetchInfo()
      for (const r of toRouteRecords(auth.menus)) {
        router.addRoute('layout', r)
      }
      // 用原始路径重新解析一次，确保新注册的路由能被匹配
      return { path: to.fullPath, replace: true, force: true }
    } catch {
      auth.reset()
      return '/login'
    }
  }
  if (to.name === 'not-found') return '/'
  return true
})

router.afterEach(() => {
  NProgress.done()
})

router.onError(() => {
  NProgress.done()
})

export default router
