import axios, { type AxiosRequestConfig } from 'axios'
import { useAuthStore } from '@/stores/auth'
import router from '@/router'
import type { ApiResult } from '@/types/api'

/**
 * 统一 HTTP 客户端：
 * - 请求自动携带 Bearer Token；
 * - 响应拦截器拆包 ApiResult，业务失败（code != 200）统一抛错；
 * - 401 时清理会话并跳转登录页。
 */
const instance = axios.create({ baseURL: '/api', timeout: 15000 })

instance.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.token) config.headers.Authorization = `Bearer ${auth.token}`
  return config
})

instance.interceptors.response.use(
  (resp) => {
    // 二进制下载（Excel 等）不按统一返回体解包
    if (resp.config.responseType === 'blob') return resp
    const res = resp.data as ApiResult
    if (res.code === 200) return resp
    if (res.code === 401) {
      useAuthStore().reset()
      router.push('/login')
    }
    return Promise.reject(new Error(res.msg || '请求失败'))
  },
  (err: { response?: { data?: ApiResult } }) => {
    const res = err.response?.data
    if (res?.code === 401) {
      useAuthStore().reset()
      router.push('/login')
    }
    return Promise.reject(new Error(res?.msg || '网络异常'))
  },
)

/** 类型化请求方法：直接返回后端 data 字段 */
export const http = {
  async get<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
    const resp = await instance.get<ApiResult<T>>(url, config)
    return resp.data.data
  },
  async post<T>(url: string, body?: unknown, config?: AxiosRequestConfig): Promise<T> {
    const resp = await instance.post<ApiResult<T>>(url, body, config)
    return resp.data.data
  },
  async put<T>(url: string, body?: unknown, config?: AxiosRequestConfig): Promise<T> {
    const resp = await instance.put<ApiResult<T>>(url, body, config)
    return resp.data.data
  },
  async delete<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
    const resp = await instance.delete<ApiResult<T>>(url, config)
    return resp.data.data
  },
}

export default instance
