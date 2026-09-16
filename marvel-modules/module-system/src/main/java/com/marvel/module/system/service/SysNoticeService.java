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

    /** 我的消息：已发布公告 + 指定用户的已读状态（可按未读过滤） */
    IPage<Map<String, Object>> myNotices(Long userId, long pageNum, long pageSize, boolean onlyUnread);

    /** 未读消息数 */
    long unreadCount(Long userId);

    /** 标记单条已读（幂等） */
    void markRead(Long userId, Long noticeId);

    /** 标记全部已读 */
    void markAllRead(Long userId);
}
