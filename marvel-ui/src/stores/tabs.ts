import { defineStore } from 'pinia'

export interface TabItem {
  /** 路由完整路径，同时作为页签唯一标识 */
  path: string
  title: string
  icon?: string
  /** 首页固定页签：永远排最前，不可关闭、不可置顶 */
  affix?: boolean
  /** 用户置顶页签：排在固定区末尾，不可关闭 */
  pinned?: boolean
}

/** 首页固定页签：常驻页签条首位（不依赖是否访问过），不可关闭、不可置顶 */
const HOME_TAB: TabItem = {
  path: '/',
  title: '首页',
  icon: 'mdi-view-dashboard-outline',
  affix: true,
}

/**
 * 多页签 Store：首页常驻，路由切换时收录页签，支持关闭（单个/其他/左侧/右侧/全部）与置顶。
 * 批量关闭动作统一返回「应跳转到的路径」——当前页签被关掉时非空，否则停留原地。
 */
export const useTabsStore = defineStore('tabs', {
  state: () => ({
    visited: [{ ...HOME_TAB }] as TabItem[],
  }),
  getters: {
    /** 固定区（首页 + 置顶）长度，置顶/取消置顶时据此计算插入位置 */
    fixedCount(state): number {
      return state.visited.filter((t) => t.affix || t.pinned).length
    },

    /** 首页页签路径（affix 页签），右键菜单据此隐藏置顶项 */
    homePath(state): string {
      return state.visited.find((t) => t.affix)?.path ?? '/'
    },
  },
  actions: {
    addTab(route: { path: string; meta?: { title?: string; icon?: string } }): void {
      const title = route.meta?.title
      if (!title) return
      if (this.visited.some((t) => t.path === route.path)) return
      this.visited.push({
        path: route.path,
        title,
        icon: route.meta?.icon,
        affix: route.path === '/',
      })
    },

    /** 页签是否保留（不可关闭）：首页或已置顶 */
    isRetained(path: string): boolean {
      const tab = this.visited.find((t) => t.path === path)
      return !!tab && (tab.affix === true || tab.pinned === true)
    },

    /**
     * 置顶/取消置顶（显式设值，供菜单的 pin/unpin 两个动作直接调用）。
     * 固定区始终排在普通页签之前，故两种操作都把页签移到固定区边界处，
     * 其余页签相对顺序不变（同 soybean-admin）。首页本身固定，忽略置顶操作。
     */
    setPinned(path: string, pinned: boolean): void {
      const idx = this.visited.findIndex((t) => t.path === path)
      if (idx < 0) return
      const tab = this.visited[idx]
      if (tab.affix || tab.pinned === pinned) return
      tab.pinned = pinned
      this.visited.splice(idx, 1)
      this.visited.splice(this.fixedCount, 0, tab)
    },

    /** 置顶状态取反 */
    togglePin(path: string): void {
      const tab = this.visited.find((t) => t.path === path)
      if (!tab) return
      this.setPinned(path, !tab.pinned)
    },

    /**
     * 关闭页签；若关闭的是当前页，返回应跳转的相邻页签路径（优先左侧），
     * 否则返回 null（停留原地）。
     */
    removeTab(path: string, currentPath: string): string | null {
      const idx = this.visited.findIndex((t) => t.path === path)
      if (idx < 0 || this.isRetained(path)) return null
      this.visited.splice(idx, 1)
      if (path !== currentPath) return null
      // 当前页被关：取左侧页签，越界则取右侧
      const next = this.visited[Math.max(0, idx - 1)] ?? this.visited[0]
      return next?.path ?? '/'
    },

    /** 关闭其他：保留固定页签与目标页签；当前页被关时跳到目标页签 */
    closeOthers(path: string, currentPath: string): string | null {
      this.visited = this.visited.filter((t) => t.affix || t.pinned || t.path === path)
      return this.visited.some((t) => t.path === currentPath) ? null : path
    },

    /** 关闭左侧：跳过固定页签；当前页被关时跳到目标页签 */
    closeLeft(path: string, currentPath: string): string | null {
      const idx = this.visited.findIndex((t) => t.path === path)
      if (idx < 0) return null
      const removed = new Set(
        this.visited
          .slice(0, idx)
          .filter((t) => !t.affix && !t.pinned)
          .map((t) => t.path),
      )
      this.visited = this.visited.filter((t) => !removed.has(t.path))
      return removed.has(currentPath) ? path : null
    },

    /** 关闭右侧：跳过固定页签；当前页被关时跳到目标页签 */
    closeRight(path: string, currentPath: string): string | null {
      const idx = this.visited.findIndex((t) => t.path === path)
      if (idx < 0) return null
      const removed = new Set(
        this.visited
          .slice(idx + 1)
          .filter((t) => !t.affix && !t.pinned)
          .map((t) => t.path),
      )
      this.visited = this.visited.filter((t) => !removed.has(t.path))
      return removed.has(currentPath) ? path : null
    },

    /** 全部关闭：仅保留固定页签；当前页不保留时回首页 */
    closeAll(currentPath: string): string | null {
      this.visited = this.visited.filter((t) => t.affix || t.pinned)
      return this.visited.some((t) => t.path === currentPath) ? null : '/'
    },

    /** 重置（登出）：清空访问记录，首页仍常驻 */
    reset(): void {
      this.visited = [{ ...HOME_TAB }]
    },
  },
})
