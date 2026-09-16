package com.marvel.module.infra.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.IService;
import com.marvel.module.infra.entity.SysJob;
import com.marvel.module.infra.entity.SysJobLog;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 定时任务管理服务：任务 CRUD、启停、立即执行、执行日志查询。
 */
public interface SysJobService extends IService<SysJob> {

    List<SysJob> listJobs(String jobName, String status);

    void createJob(SysJob job);

    void updateJob(SysJob job);

    void deleteJobs(List<Long> jobIds);

    /** 启用/暂停任务并即时刷新调度 */
    void changeStatus(Long jobId, String status);

    /** 立即执行一次并返回耗时 ms */
    long runOnce(Long jobId);

    /** 最近执行日志（供任务管理页日志对话框使用） */
    List<SysJobLog> listLogs(Long jobId, int limit);

    /** 校验 cron 表达式并返回未来 5 次执行时间预览 */
    Map<String, Object> validateCron(String cron);

    /** 执行日志分页查询 */
    IPage<SysJobLog> pageLogs(long pageNum, long pageSize, Long jobId, String jobName, String status,
                              LocalDateTime beginTime, LocalDateTime endTime);

    /** 清空全部执行日志 */
    void cleanLogs();

    /** 按失败日志重试对应任务，返回耗时 ms */
    long retryLog(Long jobLogId);
}
