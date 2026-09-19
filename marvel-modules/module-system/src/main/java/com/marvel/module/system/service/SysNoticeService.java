package com.marvel.module.system.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.IService;
import com.marvel.module.system.entity.SysNotice;

import java.util.List;
import java.util.Map;

/**
 * 通知公告服务。
 */
public interface SysNoticeService extends IService<SysNotice> {

    List<SysNotice> listNotices(String title, String type);

    void createNotice(SysNotice notice);

    void updateNotice(SysNotice notice);

    void deleteNotices(List<Long> noticeIds);

    /**
     * 我的消息：已发布公告 + 指定用户的已读状态。
     *
     * @param onlyUnread 只看未读
     * @param title      标题模糊匹配（可空）
     * @param type       类型精确匹配（可空，1=通知 2=公告）
     */
    IPage<Map<String, Object>> myNotices(Long userId, long pageNum, long pageSize,
                                         boolean onlyUnread, String title, String type);

    /** 未读消息数 */
    long unreadCount(Long userId);

    /** 标记单条已读（幂等） */
    void markRead(Long userId, Long noticeId);

    /** 标记全部已读 */
    void markAllRead(Long userId);

    /** 删除我的消息（按用户软删除，不影响其他用户与公告本身） */
    void deleteMyNotices(Long userId, List<Long> noticeIds);
}
