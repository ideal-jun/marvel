package com.marvel.module.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.marvel.common.constant.Constants;
import com.marvel.common.exception.BusinessException;
import com.marvel.module.system.entity.SysNotice;
import com.marvel.module.system.entity.SysNoticeRead;
import com.marvel.module.system.mapper.SysNoticeMapper;
import com.marvel.module.system.mapper.SysNoticeReadMapper;
import com.marvel.module.system.service.SsePushService;
import com.marvel.module.system.service.SysNoticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 通知公告业务实现：内容管理 + 站内消息已读状态（按用户维度）。
 */
@Service
@RequiredArgsConstructor
public class SysNoticeServiceImpl extends ServiceImpl<SysNoticeMapper, SysNotice> implements SysNoticeService {

    private final SysNoticeReadMapper readMapper;
    private final SsePushService ssePushService;

    @Override
    public List<SysNotice> listNotices(String title, String type) {
        return list(new LambdaQueryWrapper<SysNotice>()
                .like(StringUtils.hasText(title), SysNotice::getTitle, title)
                .eq(StringUtils.hasText(type), SysNotice::getType, type)
                .orderByDesc(SysNotice::getNoticeId));
    }

    @Override
    public void createNotice(SysNotice notice) {
        notice.setNoticeId(null);
        this.save(notice);
        // 正常状态的新公告对所有用户可见：经 Redis 扇出到各节点，在线端即时点亮未读角标
        if (Constants.STATUS_NORMAL.equals(notice.getStatus())) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("noticeId", notice.getNoticeId());
            payload.put("title", notice.getTitle());
            payload.put("content", notice.getContent());
            payload.put("type", notice.getType());
            payload.put("createTime", notice.getCreateTime());
            ssePushService.broadcast("notice", payload);
        }
    }

    @Override
    public void updateNotice(SysNotice notice) {
        this.updateById(notice);
    }

    @Override
    public void deleteNotices(List<Long> noticeIds) {
        if (noticeIds != null && !noticeIds.isEmpty()) {
            this.removeByIds(noticeIds);
        }
    }

    @Override
    public IPage<Map<String, Object>> myNotices(Long userId, long pageNum, long pageSize,
                                                boolean onlyUnread, String title, String type) {
        LambdaQueryWrapper<SysNotice> wrapper = new LambdaQueryWrapper<SysNotice>()
                .eq(SysNotice::getStatus, Constants.STATUS_NORMAL)
                .like(StringUtils.hasText(title), SysNotice::getTitle, title)
                .eq(StringUtils.hasText(type), SysNotice::getType, type)
                .orderByDesc(SysNotice::getNoticeId);
        if (onlyUnread) {
            List<Long> readIds = readMapper.selectReadNoticeIds(userId);
            if (!readIds.isEmpty()) {
                wrapper.notIn(SysNotice::getNoticeId, readIds);
            }
        }
        IPage<SysNotice> page = this.page(new Page<>(pageNum, pageSize), wrapper);

        List<Long> pageIds = page.getRecords().stream().map(SysNotice::getNoticeId).toList();
        Set<Long> readSet = new HashSet<>();
        if (!pageIds.isEmpty()) {
            readSet.addAll(readMapper.selectReadNoticeIdsIn(userId, pageIds));
        }

        IPage<Map<String, Object>> result = new Page<>(pageNum, pageSize, page.getTotal());
        result.setRecords(page.getRecords().stream().map(n -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("noticeId", n.getNoticeId());
            row.put("title", n.getTitle());
            row.put("content", n.getContent());
            row.put("type", n.getType());
            row.put("createTime", n.getCreateTime());
            row.put("read", readSet.contains(n.getNoticeId()));
            return row;
        }).toList());
        return result;
    }

    @Override
    public long unreadCount(Long userId) {
        return readMapper.countUnread(userId);
    }

    @Override
    public void markRead(Long userId, Long noticeId) {
        SysNotice notice = getById(noticeId);
        if (notice == null || !Constants.STATUS_NORMAL.equals(notice.getStatus())) {
            throw new BusinessException("公告不存在或未发布");
        }
        if (readMapper.exists(userId, noticeId) > 0) {
            return;
        }
        readMapper.insert(toRead(userId, noticeId));
    }

    @Override
    public void markAllRead(Long userId) {
        List<Long> unreadIds = readMapper.selectUnreadNoticeIds(userId);
        if (unreadIds.isEmpty()) {
            return;
        }
        List<SysNoticeRead> rows = new ArrayList<>(unreadIds.size());
        for (Long noticeId : unreadIds) {
            rows.add(toRead(userId, noticeId));
        }
        readMapper.insert(rows);
    }

    private SysNoticeRead toRead(Long userId, Long noticeId) {
        SysNoticeRead read = new SysNoticeRead();
        read.setNoticeId(noticeId);
        read.setUserId(userId);
        read.setReadTime(LocalDateTime.now());
        return read;
    }
}
