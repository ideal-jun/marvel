package com.marvel.module.infra.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.marvel.common.annotation.JobTarget;
import com.marvel.common.exception.BusinessException;
import com.marvel.module.infra.entity.SysJob;
import com.marvel.module.infra.entity.SysJobLog;
import com.marvel.module.infra.jobs.JobInvokeTarget;
import com.marvel.module.infra.mapper.SysJobLogMapper;
import com.marvel.module.infra.mapper.SysJobMapper;
import com.marvel.module.infra.service.JobLockService;
import com.marvel.module.infra.service.JobScheduler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * 任务调度实现：基于 Spring TaskScheduler + CronTrigger。
 *
 * <p>职责边界：
 * <ul>
 *   <li>refresh()：以 sys_job 表为准全量重建调度（幂等），任务增删改/启停后调用；</li>
 *   <li>runOnce()/cron 触发：反射调用 invokeTarget（beanName.method，无参方法），成败写入 sys_job_log；</li>
 *   <li>分布式锁：多实例部署时同一任务只会被一个实例执行（见 {@link JobLockService}）；</li>
 *   <li>失败重试：{@code marvel.job.retry-times} 控制同一次触发的重试次数。</li>
 * </ul>
 */
@Slf4j
@Component
public class JobSchedulerImpl implements JobScheduler {

    private final SysJobMapper jobMapper;
    private final SysJobLogMapper jobLogMapper;
    private final TaskScheduler taskScheduler;
    private final ApplicationContext applicationContext;
    private final JobLockService jobLockService;
    /** 单次触发失败后的重试次数（不含首次执行） */
    private final int retryTimes;

    /** jobId → 已注册的调度句柄 */
    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    @Autowired
    public JobSchedulerImpl(SysJobMapper jobMapper,
                            SysJobLogMapper jobLogMapper,
                            TaskScheduler taskScheduler,
                            ApplicationContext applicationContext,
                            JobLockService jobLockService,
                            @Value("${marvel.job.retry-times:1}") int retryTimes) {
        this.jobMapper = jobMapper;
        this.jobLogMapper = jobLogMapper;
        this.taskScheduler = taskScheduler;
        this.applicationContext = applicationContext;
        this.jobLockService = jobLockService;
        this.retryTimes = retryTimes;
    }

    /** 启动时按库内任务初始化调度 */
    @Autowired
    public void initOnStartup() {
        refresh();
    }

    @Override
    public synchronized void refresh() {
        // 先取消全部，再按库内启用任务重建，保证与数据库最终一致
        scheduledTasks.values().forEach(future -> future.cancel(false));
        scheduledTasks.clear();

        List<SysJob> enabledJobs = jobMapper.selectList(new LambdaQueryWrapper<SysJob>()
                .eq(SysJob::getStatus, "0"));
        enabledJobs.forEach(this::register);
        log.info("定时任务调度已刷新，当前启用任务数: {}", enabledJobs.size());
    }

    private void register(SysJob job) {
        try {
            CronTrigger trigger = new CronTrigger(job.getCronExpression());
            ScheduledFuture<?> future = taskScheduler.schedule(
                    () -> executeWithLog(job.getJobId()), trigger);
            scheduledTasks.put(job.getJobId(), future);
        } catch (IllegalArgumentException e) {
            // cron 非法不允许影响其余任务的注册，仅记录失败日志
            log.error("任务[{}] cron 表达式非法: {}", job.getJobName(), job.getCronExpression(), e);
            saveLog(job.getJobId(), job.getJobName(), "1", "cron 表达式非法: " + e.getMessage(),
                    System.currentTimeMillis());
        }
    }

    @Override
    public long runOnce(SysJob job) {
        String lockToken = jobLockService.tryLock(job.getJobId());
        if (lockToken == null) {
            throw new BusinessException("任务正在执行中，请稍后再试");
        }
        long start = System.currentTimeMillis();
        try {
            invokeWithRetry(job.getInvokeTarget());
            saveLog(job.getJobId(), job.getJobName(), "0", null, start);
            return System.currentTimeMillis() - start;
        } catch (Exception e) {
            String msg = message(e);
            saveLog(job.getJobId(), job.getJobName(), "1", msg, start);
            log.error("任务[{}]手动执行失败: {}", job.getJobName(), msg, e);
            throw new BusinessException("任务执行失败：" + msg);
        } finally {
            jobLockService.unlock(job.getJobId(), lockToken);
        }
    }

    /** cron 触发入口：按 id 回查任务；加分布式锁避免多实例重复执行，异常不外泄防止中断调度线程 */
    private void executeWithLog(Long jobId) {
        SysJob job = jobMapper.selectById(jobId);
        if (job == null) {
            return;
        }
        String lockToken = jobLockService.tryLock(jobId);
        if (lockToken == null) {
            log.info("任务[{}]已被其他实例执行，跳过本次调度", job.getJobName());
            return;
        }
        long start = System.currentTimeMillis();
        try {
            invokeWithRetry(job.getInvokeTarget());
            saveLog(job.getJobId(), job.getJobName(), "0", null, start);
        } catch (Exception e) {
            String msg = message(e);
            saveLog(job.getJobId(), job.getJobName(), "1", msg, start);
            log.error("定时任务[{}]执行失败: {}", job.getJobName(), msg, e);
        } finally {
            jobLockService.unlock(jobId, lockToken);
        }
    }

    /** 失败自动重试：最多执行 retryTimes + 1 次 */
    private void invokeWithRetry(String invokeTarget) throws Exception {
        int maxAttempts = Math.max(0, retryTimes) + 1;
        Exception last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                invokeTarget(invokeTarget);
                return;
            } catch (Exception e) {
                last = e;
                if (attempt < maxAttempts) {
                    log.warn("调用目标 {} 第 {} 次失败，将重试: {}", invokeTarget, attempt, e.getMessage());
                }
            }
        }
        throw last;
    }

    /**
     * 反射调用目标方法。格式：beanName.method（无参方法）。
     * 目标 Bean 必须托管在 Spring 容器中（如内置的 sampleJob）。
     */
    private void invokeTarget(String invokeTarget) throws Exception {
        if (!JobInvokeTarget.isValidFormat(invokeTarget)) {
            throw new IllegalArgumentException("调用目标格式应为 beanName.method（仅允许字母、数字、下划线）");
        }
        String target = invokeTarget.trim();
        Object bean = applicationContext.getBean(JobInvokeTarget.beanName(target));
        Method method = bean.getClass().getMethod(JobInvokeTarget.methodName(target));
        // invokeTarget 来自数据库/管理接口，属用户可控输入：仅允许显式标注 @JobTarget 的
        // 公开无参方法，避免调用容器内任意 Bean 的任意无参方法（不安全反射，CWE-470）。
        Method specific = AopUtils.getMostSpecificMethod(method, AopUtils.getTargetClass(bean));
        if (!AnnotatedElementUtils.hasAnnotation(specific, JobTarget.class)) {
            throw new IllegalArgumentException("调用目标未授权（缺少 @JobTarget）：" + target);
        }
        method.invoke(bean);
    }

    private String message(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private void saveLog(Long jobId, String jobName, String status, String errorMsg, long startMillis) {
        SysJobLog jobLog = new SysJobLog();
        jobLog.setJobId(jobId);
        jobLog.setJobName(jobName);
        jobLog.setStatus(status);
        jobLog.setErrorMsg(errorMsg);
        jobLog.setStartTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(startMillis), ZoneId.systemDefault()));
        jobLog.setEndTime(LocalDateTime.now());
        jobLogMapper.insert(jobLog);
    }
}
