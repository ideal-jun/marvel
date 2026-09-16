package com.marvel.module.infra.service.impl;

import com.marvel.common.exception.BusinessException;
import com.marvel.module.infra.entity.SysJobLog;
import com.marvel.module.infra.mapper.SysJobLogMapper;
import com.marvel.module.infra.service.JobScheduler;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SysJobServiceImplTest {

    private SysJobServiceImpl service(SysJobLogMapper logMapper) {
        return new SysJobServiceImpl(logMapper, mock(JobScheduler.class));
    }

    @Test
    void validateCronReturnsNextTimes() {
        Map<String, Object> result = service(mock(SysJobLogMapper.class)).validateCron("0 0 0 * * ?");

        assertThat(result.get("valid")).isEqualTo(true);
        assertThat((List<?>) result.get("nextTimes")).hasSize(5);
    }

    @Test
    void validateCronRejectsInvalidExpression() {
        Map<String, Object> result = service(mock(SysJobLogMapper.class)).validateCron("not-a-cron");

        assertThat(result.get("valid")).isEqualTo(false);
        assertThat(result.get("message")).isNotNull();
    }

    @Test
    void retryOnlyAllowsFailedRecords() {
        SysJobLog logRow = new SysJobLog();
        logRow.setJobLogId(1L);
        logRow.setJobId(9L);
        logRow.setStatus("0");
        SysJobLogMapper logMapper = mock(SysJobLogMapper.class);
        when(logMapper.selectById(1L)).thenReturn(logRow);

        assertThatThrownBy(() -> service(logMapper).retryLog(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅失败记录");
    }
}
