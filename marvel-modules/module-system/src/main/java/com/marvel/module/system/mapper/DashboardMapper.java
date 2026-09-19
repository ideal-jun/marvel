package com.marvel.module.system.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 首页看板统计 Mapper（只读聚合查询）。
 *
 * <p>聚合维度限定在 system 域内的表；任务/文件等 infra 域统计由 infra 侧接口提供，
 * 保持模块边界（未来拆分服务时各自返回自己的统计）。
 */
@Mapper
public interface DashboardMapper {

    @Select("SELECT COUNT(*) FROM sys_user")
    long countUsers();

    @Select("SELECT COUNT(*) FROM sys_user WHERE status = '0'")
    long countEnabledUsers();

    @Select("SELECT COUNT(*) FROM sys_role")
    long countRoles();

    @Select("SELECT COUNT(*) FROM sys_dept")
    long countDepts();

    @Select("SELECT COUNT(*) FROM sys_notice WHERE status = '0'")
    long countNotices();

    @Select("SELECT COUNT(*) FROM sys_logininfor WHERE login_time >= #{from}")
    long countLoginsSince(@Param("from") LocalDateTime from);

    @Select("SELECT COUNT(*) FROM sys_oper_log WHERE oper_time >= #{from}")
    long countOpersSince(@Param("from") LocalDateTime from);

    /** 登录趋势：按日 + 状态（0=成功 1=失败）分组 */
    @Select("SELECT DATE_FORMAT(login_time, '%Y-%m-%d') AS d, status AS s, COUNT(*) AS c "
            + "FROM sys_logininfor WHERE login_time >= #{from} GROUP BY d, s ORDER BY d")
    List<Map<String, Object>> loginTrend(@Param("from") LocalDateTime from);

    /** 操作趋势：按日分组 */
    @Select("SELECT DATE_FORMAT(oper_time, '%Y-%m-%d') AS d, COUNT(*) AS c "
            + "FROM sys_oper_log WHERE oper_time >= #{from} GROUP BY d ORDER BY d")
    List<Map<String, Object>> operTrend(@Param("from") LocalDateTime from);

    /** 操作日志按模块标题分布（Top N） */
    @Select("SELECT title AS name, COUNT(*) AS value FROM sys_oper_log "
            + "WHERE title IS NOT NULL GROUP BY title ORDER BY value DESC LIMIT #{limit}")
    List<Map<String, Object>> operByModule(@Param("limit") int limit);
}
