package com.marvel.module.system.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.IService;
import com.marvel.module.system.entity.SysOperLog;

import java.time.LocalDateTime;

/**
 * 操作日志 Service。
 */
public interface SysOperLogService extends IService<SysOperLog> {

    /**
     * 分页查询操作日志。
     *
     * @param title        模块标题（模糊）
     * @param operUser     操作人（模糊）
     * @param status       状态（0/1）
     * @param businessType 操作类型（INSERT/UPDATE/...，按 method 前缀匹配）
     * @param beginTime    操作时间起（含）
     * @param endTime      操作时间止（含）
     */
    IPage<SysOperLog> pageLogs(long pageNum, long pageSize, String title, String operUser,
                               String status, String businessType, LocalDateTime beginTime, LocalDateTime endTime);

    /** 清空全部操作日志 */
    void clean();
}
