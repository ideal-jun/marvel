import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useTabsStore } from './tabs'

/** 构造路由对象（TabsBar 只用到 path 与 meta.title/icon） */
function route(path: string, title?: string): { path: string; meta?: { title?: string } } {
  return title ? { path, meta: { title } } : { path }
}

/** 预置页签：/（首页固定）+ a b c d 四个普通页，返回当前路径便捷断言 */
function seedPaths(store: ReturnType<typeof useTabsStore>): void {
  for (const [p, t] of [
    ['/', '首页'],
    ['/a', 'A'],
    ['/b', 'B'],
    ['/c', 'C'],
    ['/d', 'D'],
  ] as const) {
    store.addTab(route(p, t))
  }
}

describe('stores/tabs', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  describe('addTab', () => {
    it('首页常驻：初始即存在、affix 固定，再次访问不重复收录', () => {
      const tabs = useTabsStore()
      expect(tabs.visited).toHaveLength(1)
      expect(tabs.visited[0]).toMatchObject({ path: '/', title: '首页', affix: true })
      tabs.addTab(route('/', '首页'))
      expect(tabs.visited).toHaveLength(1)
    })

    it('收录带标题的路由', () => {
      const tabs = useTabsStore()
      tabs.addTab(route('/a', 'A'))
      expect(tabs.visited.map((t) => t.path)).toEqual(['/', '/a'])
      expect(tabs.visited[1].affix).toBe(false)
    })

    it('无标题路由不收录（如 404/重定向中间态）', () => {
      const tabs = useTabsStore()
      tabs.addTab(route('/x'))
      expect(tabs.visited.map((t) => t.path)).toEqual(['/'])
    })

    it('重复路径去重', () => {
      const tabs = useTabsStore()
      tabs.addTab(route('/a', 'A'))
      tabs.addTab(route('/a', 'A'))
      expect(tabs.visited).toHaveLength(2)
    })
  })

  describe('removeTab', () => {
    it('关闭当前页返回左邻页签路径', () => {
      const tabs = useTabsStore()
      seedPaths(tabs)
      expect(tabs.removeTab('/c', '/c')).toBe('/b')
      expect(tabs.visited.map((t) => t.path)).toEqual(['/', '/a', '/b', '/d'])
    })

    it('关闭最左普通页签时回退到首页', () => {
      const tabs = useTabsStore()
      seedPaths(tabs)
      expect(tabs.removeTab('/a', '/a')).toBe('/')
    })

    it('关闭非当前页签返回 null（停留原地）', () => {
      const tabs = useTabsStore()
      seedPaths(tabs)
      expect(tabs.removeTab('/a', '/c')).toBeNull()
      expect(tabs.visited).toHaveLength(4)
    })

    it('首页与置顶页签不可关闭', () => {
      const tabs = useTabsStore()
      seedPaths(tabs)
      expect(tabs.removeTab('/', '/')).toBeNull()
      tabs.togglePin('/b')
      expect(tabs.removeTab('/b', '/b')).toBeNull()
      expect(tabs.visited).toHaveLength(5)
    })
  })

  describe('togglePin', () => {
    it('置顶移动到固定区末尾，取消置顶移到固定区之后', () => {
      const tabs = useTabsStore()
      seedPaths(tabs)
      tabs.togglePin('/c')
      expect(tabs.visited.map((t) => t.path)).toEqual(['/', '/c', '/a', '/b', '/d'])
      expect(tabs.isRetained('/c')).toBe(true)
      tabs.togglePin('/d')
      expect(tabs.visited.map((t) => t.path)).toEqual(['/', '/c', '/d', '/a', '/b'])
      tabs.togglePin('/c')
      // 取消置顶：c 落到固定区（/ 与置顶 d）之后
      expect(tabs.visited.map((t) => t.path)).toEqual(['/', '/d', '/c', '/a', '/b'])
      expect(tabs.isRetained('/c')).toBe(false)
    })

    it('首页不可置顶', () => {
      const tabs = useTabsStore()
      seedPaths(tabs)
      tabs.togglePin('/')
      expect(tabs.visited[0].pinned).toBeUndefined()
      expect(tabs.visited.map((t) => t.path)).toEqual(['/', '/a', '/b', '/c', '/d'])
    })

    it('setPinned 显式置顶/取消置顶（幂等，不重复移动）', () => {
      const tabs = useTabsStore()
      seedPaths(tabs)
      tabs.setPinned('/c', true)
      expect(tabs.visited.map((t) => t.path)).toEqual(['/', '/c', '/a', '/b', '/d'])
      tabs.setPinned('/c', true)
      expect(tabs.visited.map((t) => t.path)).toEqual(['/', '/c', '/a', '/b', '/d'])
      // 取消置顶：落在固定区边界（同 soybean unfixTab），不回原位置
      tabs.setPinned('/c', false)
      expect(tabs.visited.map((t) => t.path)).toEqual(['/', '/c', '/a', '/b', '/d'])
      expect(tabs.isRetained('/c')).toBe(false)
      expect(tabs.homePath).toBe('/')
    })
  })

  describe('批量关闭', () => {
    it('closeOthers 保留固定页签与目标，当前页被关时返回目标路径', () => {
      const tabs = useTabsStore()
      seedPaths(tabs)
      tabs.togglePin('/b')
      // [/, b(置顶), a, c, d]，右键 d 关闭其他
      expect(tabs.closeOthers('/d', '/c')).toBe('/d')
      expect(tabs.visited.map((t) => t.path)).toEqual(['/', '/b', '/d'])
    })

    it('closeOthers 目标即当前页时返回 null', () => {
      const tabs = useTabsStore()
      seedPaths(tabs)
      expect(tabs.closeOthers('/c', '/c')).toBeNull()
      expect(tabs.visited.map((t) => t.path)).toEqual(['/', '/c'])
    })

    it('closeLeft 跳过固定页签，当前页被关时返回目标路径', () => {
      const tabs = useTabsStore()
      seedPaths(tabs)
      tabs.togglePin('/b')
      // [/, b(置顶), a, c, d]，右键 c 关闭左侧 → 移除 a（/, b 保留）
      expect(tabs.closeLeft('/c', '/a')).toBe('/c')
      expect(tabs.visited.map((t) => t.path)).toEqual(['/', '/b', '/c', '/d'])
    })

    it('closeLeft 当前页未受影响时返回 null', () => {
      const tabs = useTabsStore()
      seedPaths(tabs)
      expect(tabs.closeLeft('/c', '/c')).toBeNull()
      expect(tabs.visited.map((t) => t.path)).toEqual(['/', '/c', '/d'])
    })

    it('closeRight 移除右侧普通页签', () => {
      const tabs = useTabsStore()
      seedPaths(tabs)
      expect(tabs.closeRight('/b', '/b')).toBeNull()
      expect(tabs.visited.map((t) => t.path)).toEqual(['/', '/a', '/b'])
    })

    it('closeRight 右侧有置顶页签时跳过它，当前页被关时返回目标路径', () => {
      const tabs = useTabsStore()
      seedPaths(tabs)
      tabs.togglePin('/d')
      // [/, d(置顶), a, b, c]，右键 b 关闭右侧 → 移除 c，d 保留
      expect(tabs.closeRight('/b', '/c')).toBe('/b')
      expect(tabs.visited.map((t) => t.path)).toEqual(['/', '/d', '/a', '/b'])
    })

    it('closeAll 仅保留固定页签，当前不保留时回首页', () => {
      const tabs = useTabsStore()
      seedPaths(tabs)
      tabs.togglePin('/b')
      expect(tabs.closeAll('/c')).toBe('/')
      expect(tabs.visited.map((t) => t.path)).toEqual(['/', '/b'])
    })

    it('closeAll 当前页被置顶保留时返回 null', () => {
      const tabs = useTabsStore()
      seedPaths(tabs)
      tabs.togglePin('/c')
      expect(tabs.closeAll('/c')).toBeNull()
      expect(tabs.visited.map((t) => t.path)).toEqual(['/', '/c'])
    })
  })

  it('reset 清空访问记录，首页仍常驻', () => {
    const tabs = useTabsStore()
    seedPaths(tabs)
    tabs.reset()
    expect(tabs.visited).toHaveLength(1)
    expect(tabs.visited[0]).toMatchObject({ path: '/', affix: true })
  })
})
