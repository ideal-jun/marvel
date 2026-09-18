package com.marvel.module.system.service.impl;

import com.marvel.module.system.entity.SysNotice;
import com.marvel.module.system.entity.SysNoticeRead;
import com.marvel.module.system.mapper.SysNoticeMapper;
import com.marvel.module.system.mapper.SysNoticeReadMapper;
import com.marvel.module.system.service.SsePushService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SysNoticeServiceImplTest {

    private SysNoticeServiceImpl service(SysNoticeReadMapper readMapper, SysNoticeMapper noticeMapper) {
        SysNoticeServiceImpl service = new SysNoticeServiceImpl(readMapper, mock(SsePushService.class));
        ReflectionTestUtils.setField(service, "baseMapper", noticeMapper);
        return service;
    }

    private SysNotice published() {
        SysNotice notice = new SysNotice();
        notice.setNoticeId(1L);
        notice.setStatus("0");
        return notice;
    }

    @Test
    void markReadInsertsWhenNotRead() {
        SysNoticeReadMapper readMapper = mock(SysNoticeReadMapper.class);
        SysNoticeMapper noticeMapper = mock(SysNoticeMapper.class);
        when(noticeMapper.selectById(1L)).thenReturn(published());
        when(readMapper.exists(7L, 1L)).thenReturn(0);

        service(readMapper, noticeMapper).markRead(7L, 1L);

        verify(readMapper).insert(any(SysNoticeRead.class));
    }

    @Test
    void markReadIsIdempotent() {
        SysNoticeReadMapper readMapper = mock(SysNoticeReadMapper.class);
        SysNoticeMapper noticeMapper = mock(SysNoticeMapper.class);
        when(noticeMapper.selectById(1L)).thenReturn(published());
        when(readMapper.exists(7L, 1L)).thenReturn(1);

        service(readMapper, noticeMapper).markRead(7L, 1L);

        verify(readMapper, never()).insert(any(SysNoticeRead.class));
    }

    @Test
    void unreadCountDelegatesToMapper() {
        SysNoticeReadMapper readMapper = mock(SysNoticeReadMapper.class);
        when(readMapper.countUnread(7L)).thenReturn(3L);

        assertThat(service(readMapper, mock(SysNoticeMapper.class)).unreadCount(7L)).isEqualTo(3L);
    }
}
