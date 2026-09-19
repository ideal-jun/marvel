package com.marvel.module.system.controller;

import com.marvel.common.result.R;
import com.marvel.module.system.service.SysDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 首页看板统计接口（登录即可访问，不含敏感明细）。
 *
 * <p>仅提供 system 域聚合数据；任务等 infra 统计由 {@code /infra/dashboard/**} 提供。
 */
@RestController
@RequestMapping("/system/dashboard")
@RequiredArgsConstructor
public class SysDashboardController {

    private final SysDashboardService dashboardService;

    @GetMapping("/stats")
    public R<Map<String, Object>> stats() {
        return R.ok(dashboardService.stats());
    }

    /** 近 N 天登录趋势（1-30 天） */
    @GetMapping("/login-trend")
    public R<List<Map<String, Object>>> loginTrend(@RequestParam(defaultValue = "7") int days) {
        return R.ok(dashboardService.loginTrend(clampDays(days)));
    }

    /** 近 N 天操作趋势（1-30 天） */
    @GetMapping("/oper-trend")
    public R<List<Map<String, Object>>> operTrend(@RequestParam(defaultValue = "7") int days) {
        return R.ok(dashboardService.operTrend(clampDays(days)));
    }

    /** 操作日志模块分布（Top N） */
    @GetMapping("/oper-module")
    public R<List<Map<String, Object>>> operModule(@RequestParam(defaultValue = "6") int limit) {
        return R.ok(dashboardService.operByModule(Math.min(Math.max(limit, 1), 20)));
    }

    private int clampDays(int days) {
        return Math.min(Math.max(days, 1), 30);
    }
}
