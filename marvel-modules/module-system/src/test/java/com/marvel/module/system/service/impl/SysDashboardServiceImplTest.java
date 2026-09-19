package com.marvel.module.system.service.impl;

import com.marvel.module.system.mapper.DashboardMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 看板统计日期补零逻辑单测（纯函数式聚合，不依赖容器）。
 */
class SysDashboardServiceImplTest {

    private final DashboardMapper mapper = mock(DashboardMapper.class);
    private final SysDashboardServiceImpl service = new SysDashboardServiceImpl(mapper);

    private Map<String, Object> row(String date, String status, long count) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("d", date);
        row.put("s", status);
        row.put("c", count);
        return row;
    }

    @Test
    void loginTrendFillsMissingDatesWithZero() {
        LocalDate today = LocalDate.now();
        when(mapper.loginTrend(any(LocalDateTime.class))).thenReturn(List.of(
                row(today.toString(), "0", 4L),
                row(today.minusDays(2).toString(), "1", 2L)));

        List<Map<String, Object>> trend = service.loginTrend(7);

        assertThat(trend).hasSize(7);
        assertThat(trend.get(6).get("date")).isEqualTo(today.toString());
        assertThat(trend.get(6).get("success")).isEqualTo(4L);
        assertThat(trend.get(6).get("fail")).isEqualTo(0L);
        // 倒数第三天命中失败记录
        assertThat(trend.get(4).get("fail")).isEqualTo(2L);
        // 无数据的日期补 0 而非缺失
        assertThat(trend.get(0).get("success")).isEqualTo(0L);
        assertThat(trend.get(0).get("fail")).isEqualTo(0L);
    }

    @Test
    void operTrendReturnsContinuousDatesWithZero() {
        when(mapper.operTrend(any(LocalDateTime.class))).thenReturn(List.of());

        List<Map<String, Object>> trend = service.operTrend(3);

        assertThat(trend).hasSize(3);
        assertThat(trend).allSatisfy(point -> assertThat(point.get("count")).isEqualTo(0L));
    }
}
