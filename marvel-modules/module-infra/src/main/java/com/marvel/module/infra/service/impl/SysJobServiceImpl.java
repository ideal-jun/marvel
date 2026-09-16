package com.marvel.module.infra.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.marvel.common.exception.BusinessException;
import com.marvel.module.infra.entity.SysJob;
import com.marvel.module.infra.entity.SysJobLog;
import com.marvel.module.infra.jobs.JobInvokeTarget;
import com.marvel.module.infra.mapper.SysJobLogMapper;
import com.marvel.module.infra.mapper.SysJobMapper;
import com.marvel.module.infra.service.JobScheduler;
import com.marvel.module.infra.service.SysJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 定时任务管理业务实现。
 *
 * <p>关键规则：
 * <ul>
 *   <li>cron 与 invokeTarget 在保存前做基础校验；</li>
 *   <li>增删改/启停后同步刷新调度器，保证运行态与库内数据一致；</li>
 *   <li>删除任务时级联删除其执行日志。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class SysJobServiceImpl extends ServiceImpl<SysJobMapper, SysJob> implements SysJobService {

    private final SysJobLogMapper jobLogMapper;
    private final JobScheduler jobScheduler;

    @Override
    public List<SysJob> listJobs(String jobName, String status) {
        return list(new LambdaQueryWrapper<SysJob>()
                .like(StringUtils.hasText(jobName), SysJob::getJobName, jobName)
                .eq(StringUtils.hasText(status), SysJob::getStatus, status)
                .orderByAsc(SysJob::getJobId));
    }

    @Override
    @Transactional
    public void createJob(SysJob job) {
        validate(job);
        job.setJobId(null);
        this.save(job);
        jobScheduler.refresh();
    }

    @Override
    @Transactional
    public void updateJob(SysJob job) {
        if (getById(job.getJobId()) == null) {
            throw new BusinessException("任务不存在");
        }
        validate(job);
        this.updateById(job);
        jobScheduler.refresh();
    }

    @Override
    @Transactional
    public void deleteJobs(List<Long> jobIds) {
        if (jobIds == null || jobIds.isEmpty()) {
            return;
        }
        this.removeByIds(jobIds);
        // 级联清理执行日志
        jobLogMapper.delete(new LambdaQueryWrapper<SysJobLog>().in(SysJobLog::getJobId, jobIds));
        jobScheduler.refresh();
    }

    @Override
    public void changeStatus(Long jobId, String status) {
        SysJob job = getById(jobId);
        if (job == null) {
            throw new BusinessException("任务不存在");
        }
        SysJob update = new SysJob();
        update.setJobId(jobId);
        update.setStatus(status);
        this.updateById(update);
        jobScheduler.refresh();
    }

    @Override
    public long runOnce(Long jobId) {
        SysJob job = getById(jobId);
        if (job == null) {
            throw new BusinessException("任务不存在");
        }
        return jobScheduler.runOnce(job);
    }

    @Override
    public List<SysJobLog> listLogs(Long jobId, int limit) {
        return jobLogMapper.selectList(new LambdaQueryWrapper<SysJobLog>()
                .eq(jobId != null, SysJobLog::getJobId, jobId)
                .orderByDesc(SysJobLog::getJobLogId)
                .last("LIMIT " + Math.max(1, Math.min(limit, 200))));
    }

    @Override
    public Map<String, Object> validateCron(String cron) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!StringUtils.hasText(cron)) {
            result.put("valid", false);
            result.put("message", "cron 表达式不能为空");
            return result;
        }
        try {
            CronExpression expression = CronExpression.parse(cron);
            List<String> nextTimes = new ArrayList<>();
            LocalDateTime time = LocalDateTime.now();
            for (int i = 0; i < 5; i++) {
                time = expression.next(time);
                if (time == null) {
                    break;
                }
                nextTimes.add(time.toString());
            }
            result.put("valid", true);
            result.put("nextTimes", nextTimes);
        } catch (IllegalArgumentException e) {
            result.put("valid", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    @Override
    public IPage<SysJobLog> pageLogs(long pageNum, long pageSize, Long jobId, String jobName, String status,
                                     LocalDateTime beginTime, LocalDateTime endTime) {
        return jobLogMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<SysJobLog>()
                        .eq(jobId != null, SysJobLog::getJobId, jobId)
                        .like(StringUtils.hasText(jobName), SysJobLog::getJobName, jobName)
                        .eq(StringUtils.hasText(status), SysJobLog::getStatus, status)
                        .ge(beginTime != null, SysJobLog::getStartTime, beginTime)
                        .le(endTime != null, SysJobLog::getStartTime, endTime)
                        .orderByDesc(SysJobLog::getJobLogId));
    }

    @Override
    public void cleanLogs() {
        jobLogMapper.delete(new LambdaQueryWrapper<>());
    }

    @Override
    public long retryLog(Long jobLogId) {
        SysJobLog logRow = jobLogMapper.selectById(jobLogId);
        if (logRow == null) {
            throw new BusinessException("执行日志不存在");
        }
        if (!"1".equals(logRow.getStatus())) {
            throw new BusinessException("仅失败记录支持重试");
        }
        return runOnce(logRow.getJobId());
    }

    /** 保存前基础校验：cron 表达式合法性（CronExpression 解析）与必填项 */
    private void validate(SysJob job) {
        if (!StringUtils.hasText(job.getJobName())) {
            throw new BusinessException("任务名称不能为空");
        }
        if (!JobInvokeTarget.isValidFormat(job.getInvokeTarget())) {
            throw new BusinessException("调用目标格式应为 beanName.method（仅允许字母、数字、下划线）");
        }
        if (!StringUtils.hasText(job.getCronExpression())) {
            throw new BusinessException("cron 表达式不能为空");
        }
        try {
            CronExpression.parse(job.getCronExpression());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("cron 表达式非法：" + e.getMessage());
        }
    }
}
