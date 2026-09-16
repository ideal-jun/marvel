package com.marvel.module.infra.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.marvel.common.annotation.Log;
import com.marvel.common.result.R;
import com.marvel.module.infra.entity.SysJob;
import com.marvel.module.infra.entity.SysJobLog;
import com.marvel.module.infra.service.SysJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 定时任务管理接口，路径前缀 /infra/**（与未来网关路由一致）。
 */
@RestController
@RequestMapping("/infra/job")
@RequiredArgsConstructor
public class SysJobController {

    private final SysJobService jobService;

    @SaCheckPermission("infra:job:list")
    @GetMapping("/list")
    public R<List<SysJob>> list(@RequestParam(required = false) String jobName,
                                @RequestParam(required = false) String status) {
        return R.ok(jobService.listJobs(jobName, status));
    }

    @SaCheckPermission("infra:job:query")
    @GetMapping("/{jobId}")
    public R<SysJob> detail(@PathVariable Long jobId) {
        return R.ok(jobService.getById(jobId));
    }

    @SaCheckPermission("infra:job:add")
    @Log(title = "定时任务", businessType = Log.BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@RequestBody SysJob job) {
        jobService.createJob(job);
        return R.ok();
    }

    @SaCheckPermission("infra:job:edit")
    @Log(title = "定时任务", businessType = Log.BusinessType.UPDATE)
    @PutMapping
    public R<Void> update(@RequestBody SysJob job) {
        jobService.updateJob(job);
        return R.ok();
    }

    @SaCheckPermission("infra:job:remove")
    @Log(title = "定时任务", businessType = Log.BusinessType.DELETE)
    @DeleteMapping("/{jobIds}")
    public R<Void> remove(@PathVariable List<Long> jobIds) {
        jobService.deleteJobs(jobIds);
        return R.ok();
    }

    /** 启用/暂停任务（status: 0=正常 1=暂停） */
    @SaCheckPermission("infra:job:edit")
    @Log(title = "定时任务", businessType = Log.BusinessType.UPDATE)
    @PutMapping("/changeStatus")
    public R<Void> changeStatus(@RequestParam Long jobId, @RequestParam String status) {
        jobService.changeStatus(jobId, status);
        return R.ok();
    }

    /** 立即执行一次，返回耗时 ms */
    @SaCheckPermission("infra:job:run")
    @Log(title = "定时任务", businessType = Log.BusinessType.UPDATE)
    @PutMapping("/run/{jobId}")
    public R<Long> run(@PathVariable Long jobId) {
        return R.ok("执行成功", jobService.runOnce(jobId));
    }

    /** 任务执行日志（默认最近 50 条） */
    @SaCheckPermission("infra:job:list")
    @GetMapping("/logs/{jobId}")
    public R<List<SysJobLog>> logs(@PathVariable Long jobId,
                                   @RequestParam(defaultValue = "50") int limit) {
        return R.ok(jobService.listLogs(jobId, limit));
    }

    /** 校验 cron 表达式并返回未来执行时间预览 */
    @SaCheckPermission("infra:job:edit")
    @GetMapping("/validate-cron")
    public R<Map<String, Object>> validateCron(@RequestParam String cron) {
        return R.ok(jobService.validateCron(cron));
    }

    /** 执行日志分页查询（支持任务/名称/状态/时间过滤） */
    @SaCheckPermission("infra:job:list")
    @GetMapping("/log/page")
    public R<IPage<SysJobLog>> logPage(@RequestParam(defaultValue = "1") long pageNum,
                                       @RequestParam(defaultValue = "10") long pageSize,
                                       @RequestParam(required = false) Long jobId,
                                       @RequestParam(required = false) String jobName,
                                       @RequestParam(required = false) String status,
                                       @RequestParam(required = false)
                                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime beginTime,
                                       @RequestParam(required = false)
                                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return R.ok(jobService.pageLogs(pageNum, pageSize, jobId, jobName, status, beginTime, endTime));
    }

    /** 清空全部执行日志 */
    @SaCheckPermission("infra:job:remove")
    @Log(title = "定时任务", businessType = Log.BusinessType.CLEAN)
    @DeleteMapping("/log/clean")
    public R<Void> cleanLogs() {
        jobService.cleanLogs();
        return R.ok();
    }

    /** 按失败日志重试对应任务 */
    @SaCheckPermission("infra:job:run")
    @Log(title = "定时任务", businessType = Log.BusinessType.UPDATE)
    @PostMapping("/retry/{jobLogId}")
    public R<Long> retry(@PathVariable Long jobLogId) {
        return R.ok("重试成功", jobService.retryLog(jobLogId));
    }
}
