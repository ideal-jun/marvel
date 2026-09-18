import { defineStore } from 'pinia'
import { http } from '@/api/request'

/**
 * 站内消息 Store：未读数被顶栏角标与「我的消息」页共同消费，
 * 集中在此以保证一处变更全局同步。
 *
 * <p>计数始终以服务端为准（不做本地推算），避免重复投递或其他端已读造成漂移。
 */
export const useNoticeStore = defineStore('notice', {
  state: () => ({
    /** 未读消息数，仅由 refreshUnread 写入 */
    unread: 0,
  }),
  actions: {
    /** 拉取未读数：失败静默（角标属旁路能力，不打扰用户） */
    async refreshUnread(): Promise<void> {
      try {
        this.unread = await http.get<number>('/system/notice/unread-count')
      } catch {
        // 忽略：保持上次数值
      }
    },

    /** 标记单条已读并同步未读数 */
    async markRead(noticeId: number): Promise<void> {
      await http.post<null>(`/system/notice/read/${noticeId}`)
      await this.refreshUnread()
    },

    /** 全部标记已读并同步未读数 */
    async markAllRead(): Promise<void> {
      await http.post<null>('/system/notice/read-all')
      await this.refreshUnread()
    },
  },
})
