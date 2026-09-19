package com.marvel.module.system.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.marvel.module.system.mapper.DashboardMapper;
import com.marvel.module.system.service.SysDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 首页看板统计实现：聚合 system 域只读查询，缺失日期补 0 保证图表 X 轴连续。
 */
@Service
@RequiredArgsConstructor
public class SysDashboardServiceImpl implements SysDashboardService {

    /** 单次最多扫描的令牌数，避免全量扫描拖垮 Redis */
    private static final int MAX_SCAN = 1000;

    private final DashboardMapper dashboardMapper;

    @Override
    public Map<String, Object> stats() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userCount", dashboardMapper.countUsers());
        data.put("enabledUserCount", dashboardMapper.countEnabledUsers());
        data.put("roleCount", dashboardMapper.countRoles());
        data.put("deptCount", dashboardMapper.countDepts());
        data.put("noticeCount", dashboardMapper.countNotices());
        data.put("onlineCount", onlineCount());
        data.put("todayLoginCount", dashboardMapper.countLoginsSince(todayStart));
        data.put("todayOperCount", dashboardMapper.countOpersSince(todayStart));
        return data;
    }

    @Override
    public List<Map<String, Object>> loginTrend(int days) {
        LocalDate today = LocalDate.now();
        LocalDateTime from = today.minusDays(days - 1L).atStartOfDay();
        Map<String, long[]> byDate = new HashMap<>();
        for (Map<String, Object> row : dashboardMapper.loginTrend(from)) {
            long[] pair = byDate.computeIfAbsent(String.valueOf(row.get("d")), k -> new long[2]);
            long count = toLong(row.get("c"));
            if ("0".equals(String.valueOf(row.get("s")))) {
                pair[0] += count;
            } else {
                pair[1] += count;
            }
        }
        List<Map<String, Object>> result = new ArrayList<>(days);
        for (int i = 0; i < days; i++) {
            String date = today.minusDays(days - 1L - i).toString();
            long[] pair = byDate.getOrDefault(date, new long[2]);
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", date);
            point.put("success", pair[0]);
            point.put("fail", pair[1]);
            result.add(point);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> operTrend(int days) {
        LocalDate today = LocalDate.now();
        LocalDateTime from = today.minusDays(days - 1L).atStartOfDay();
        Map<String, Long> byDate = new HashMap<>();
        for (Map<String, Object> row : dashboardMapper.operTrend(from)) {
            byDate.put(String.valueOf(row.get("d")), toLong(row.get("c")));
        }
        List<Map<String, Object>> result = new ArrayList<>(days);
        for (int i = 0; i < days; i++) {
            String date = today.minusDays(days - 1L - i).toString();
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", date);
            point.put("count", byDate.getOrDefault(date, 0L));
            result.add(point);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> operByModule(int limit) {
        return dashboardMapper.operByModule(limit);
    }

    /** 当前在线用户数（按登录用户去重） */
    private long onlineCount() {
        String tokenKeyPrefix = StpUtil.getStpLogic().splicingKeyTokenValue("");
        Set<Object> loginIds = new HashSet<>();
        for (String raw : StpUtil.searchTokenValue("", 0, MAX_SCAN, true)) {
            String token = raw.startsWith(tokenKeyPrefix) ? raw.substring(tokenKeyPrefix.length()) : raw;
            Object loginId = StpUtil.getLoginIdByToken(token);
            if (loginId != null) {
                loginIds.add(loginId);
            }
        }
        return loginIds.size();
    }

    private long toLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }
}
