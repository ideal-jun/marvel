package com.marvel.module.system.service;

import java.util.List;
import java.util.Map;

/** 首页看板统计服务。 */
public interface SysDashboardService {

    /** 概览：用户/角色/部门/公告数量、在线数、今日登录与操作数 */
    Map<String, Object> stats();

    /** 近 N 天登录趋势（按成功/失败），缺失日期补 0 */
    List<Map<String, Object>> loginTrend(int days);

    /** 近 N 天操作趋势，缺失日期补 0 */
    List<Map<String, Object>> operTrend(int days);

    /** 操作日志按模块分布（Top N） */
    List<Map<String, Object>> operByModule(int limit);
}
