import { defineStore } from 'pinia'
import { http } from '@/api/request'
import type { LoginBody, LoginResult, MenuNode, UserInfoResult } from '@/types/api'

interface AuthState {
  token: string
  nickname: string
  /** 头像 URL（空则用昵称首字） */
  avatar: string
  roles: string[]
  permissions: string[]
  /** 后端原始菜单树（含 menuType/component，供动态路由注册与侧边栏共用） */
  menus: MenuNode[]
}

/**
 * 认证与会话 Store：token 持久化于 localStorage，
 * 用户信息/权限/动态菜单在登录后一次拉取。
 */
export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    token: localStorage.getItem('token') ?? '',
    nickname: '',
    avatar: '',
    roles: [],
    permissions: [],
    menus: [],
  }),
  getters: {
    isLoggedIn: (s): boolean => !!s.token,
  },
  actions: {
    async login(body: LoginBody): Promise<void> {
      const data = await http.post<LoginResult>('/auth/login', body)
      this.token = data.token
      localStorage.setItem('token', this.token)
    },

    /** 拉取当前用户信息与动态路由菜单（路由守卫首次进入时调用） */
    async fetchInfo(): Promise<void> {
      const info = await http.get<UserInfoResult>('/auth/getInfo')
      this.nickname = info.user.nickname
      this.avatar = info.user.avatar ?? ''
      this.roles = info.roles
      this.permissions = info.permissions
      this.menus = (await http.get<MenuNode[]>('/auth/getRouters')) ?? []
    },

    /** 按钮级权限判断：admin 角色或权限串匹配 */
    hasPerm(perm: string): boolean {
      return this.roles.includes('admin') || this.permissions.includes(perm)
    },

    reset(): void {
      this.token = ''
      this.nickname = ''
      this.avatar = ''
      this.roles = []
      this.permissions = []
      this.menus = []
      localStorage.removeItem('token')
    },

    async logout(): Promise<void> {
      try {
        await http.post<null>('/auth/logout')
      } finally {
        this.reset()
      }
    },
  },
})
