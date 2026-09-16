package com.marvel.module.system.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.marvel.module.system.entity.SysLogininfor;
import com.marvel.module.system.entity.SysOperLog;
import com.marvel.module.system.mapper.SysLogininforMapper;
import com.marvel.module.system.mapper.SysOperLogMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LogRetentionServiceTest {

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SysOperLog.class);
        TableInfoHelper.initTableInfo(assistant, SysLogininfor.class);
    }

    @Test
    void deletesLogsOlderThanRetention() {
        SysOperLogMapper oper = mock(SysOperLogMapper.class);
        SysLogininforMapper login = mock(SysLogininforMapper.class);
        when(oper.delete(any())).thenReturn(3);
        when(login.delete(any())).thenReturn(2);

        LogRetentionService service = new LogRetentionService(oper, login);
        ReflectionTestUtils.setField(service, "retentionDays", 30);
        service.cleanExpiredLogs();

        verify(oper).delete(any());
        verify(login).delete(any());
    }

    @Test
    void skipsWhenRetentionDisabled() {
        SysOperLogMapper oper = mock(SysOperLogMapper.class);
        SysLogininforMapper login = mock(SysLogininforMapper.class);

        LogRetentionService service = new LogRetentionService(oper, login);
        ReflectionTestUtils.setField(service, "retentionDays", 0);
        service.cleanExpiredLogs();

        verifyNoInteractions(oper, login);
    }
}
