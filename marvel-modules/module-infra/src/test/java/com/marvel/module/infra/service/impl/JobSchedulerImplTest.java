package com.marvel.module.infra.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.marvel.common.annotation.JobTarget;
import com.marvel.common.exception.BusinessException;
import com.marvel.module.infra.entity.SysJob;
import com.marvel.module.infra.entity.SysJobLog;
import com.marvel.module.infra.mapper.SysJobLogMapper;
import com.marvel.module.infra.mapper.SysJobMapper;
import com.marvel.module.infra.service.JobLockService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobSchedulerImplTest {

    /** 内置演示任务等价物：@JobTarget 标注的公开无参方法才允许被调度 */
    static class SampleBean {
        final AtomicInteger invocations = new AtomicInteger();

        @JobTarget
        public void run() {
            invocations.incrementAndGet();
        }
    }

    static class FailingBean {
        @JobTarget
        public void boom() {
            throw new IllegalStateException("boom");
        }
    }

    static class UnannotatedBean {
        public void dangerous() {
        }
    }

    private SysJobMapper jobMapper;
    private SysJobLogMapper jobLogMapper;
    private TaskScheduler taskScheduler;
    private ApplicationContext applicationContext;
    private JobLockService jobLockService;
    private JobSchedulerImpl scheduler;

    @BeforeEach
    void setUp() {
        jobMapper = mock(SysJobMapper.class);
        jobLogMapper = mock(SysJobLogMapper.class);
        taskScheduler = mock(TaskScheduler.class);
        applicationContext = mock(ApplicationContext.class);
        jobLockService = mock(JobLockService.class);
        // refresh() 直接构造 LambdaQueryWrapper，需先注册实体元数据
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SysJob.class);
        scheduler = new JobSchedulerImpl(jobMapper, jobLogMapper, taskScheduler,
                applicationContext, jobLockService, 1);
    }

    private SysJob job(Long id, String invokeTarget, String cron) {
        SysJob job = new SysJob();
        job.setJobId(id);
        job.setJobName("job-" + id);
        job.setInvokeTarget(invokeTarget);
        job.setCronExpression(cron);
        job.setStatus("0");
        return job;
    }

    @Test
    void refreshRegistersEnabledJobsAndIsolatesBadCron() {
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        doReturn(future).when(taskScheduler).schedule(any(Runnable.class), any(CronTrigger.class));
        when(jobMapper.selectList(any())).thenReturn(List.of(
                job(1L, "sampleJob.run", "0 0 * * * ?"),
                job(2L, "sampleJob.run", "not-a-cron")));

        scheduler.refresh();

        // 合法任务注册调度，非法 cron 仅记录失败日志、不影响其余任务
        verify(taskScheduler, times(1)).schedule(any(Runnable.class), any(CronTrigger.class));
        ArgumentCaptor<SysJobLog> logCaptor = ArgumentCaptor.forClass(SysJobLog.class);
        verify(jobLogMapper, times(1)).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getStatus()).isEqualTo("1");
        assertThat(logCaptor.getValue().getErrorMsg()).contains("cron");
    }

    @Test
    void refreshCancelsStaleRegistrations() {
        ScheduledFuture<?> stale = mock(ScheduledFuture.class);
        doReturn(stale).when(taskScheduler).schedule(any(Runnable.class), any(CronTrigger.class));
        when(jobMapper.selectList(any())).thenReturn(
                List.of(job(1L, "sampleJob.run", "0 0 * * * ?")),
                List.of());

        scheduler.refresh();
        scheduler.refresh();

        verify(stale).cancel(false);
        // 第二次刷新后无任务，不再注册新调度
        verify(taskScheduler, times(1)).schedule(any(Runnable.class), any(CronTrigger.class));
    }

    @Test
    void runOnceLocksInvokesAndLogsSuccess() {
        SampleBean bean = new SampleBean();
        when(applicationContext.getBean("sampleJob")).thenReturn(bean);
        when(jobLockService.tryLock(1L)).thenReturn("token-1");

        long cost = scheduler.runOnce(job(1L, "sampleJob.run", "0 0 * * * ?"));

        assertThat(cost).isNotNegative();
        assertThat(bean.invocations.get()).isEqualTo(1);
        ArgumentCaptor<SysJobLog> logCaptor = ArgumentCaptor.forClass(SysJobLog.class);
        verify(jobLogMapper).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getStatus()).isEqualTo("0");
        verify(jobLockService).unlock(1L, "token-1");
    }

    @Test
    void runOnceRejectsWhenLockHeld() {
        when(jobLockService.tryLock(1L)).thenReturn(null);

        assertThatThrownBy(() -> scheduler.runOnce(job(1L, "sampleJob.run", "0 0 * * * ?")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("正在执行中");
        verify(jobLogMapper, never()).insert(any(SysJobLog.class));
    }

    @Test
    void runOnceRetriesOnFailureAndAlwaysUnlocks() {
        when(jobLockService.tryLock(1L)).thenReturn("token-1");
        when(applicationContext.getBean("failingBean")).thenReturn(new FailingBean());

        assertThatThrownBy(() -> scheduler.runOnce(job(1L, "failingBean.boom", "0 0 * * * ?")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("任务执行失败");

        ArgumentCaptor<SysJobLog> logCaptor = ArgumentCaptor.forClass(SysJobLog.class);
        verify(jobLogMapper).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getStatus()).isEqualTo("1");
        // 失败也必须释放锁（finally 语义）
        verify(jobLockService).unlock(1L, "token-1");
    }

    @Test
    void unannotatedTargetIsRefused() {
        when(jobLockService.tryLock(1L)).thenReturn("token-1");
        when(applicationContext.getBean("evil")).thenReturn(new UnannotatedBean());

        assertThatThrownBy(() -> scheduler.runOnce(job(1L, "evil.dangerous", "0 0 * * * ?")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未授权");
        verify(jobLockService).unlock(1L, "token-1");
    }

    @Test
    void malformedInvokeTargetIsRefused() {
        when(jobLockService.tryLock(1L)).thenReturn("token-1");

        // 全限定类名（多级点号）与带括号的写法均不在 beanName.method 白名单内
        assertThatThrownBy(() -> scheduler.runOnce(job(1L, "java.lang.System.exit", "0 0 * * * ?")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("格式");
        assertThatThrownBy(() -> scheduler.runOnce(job(1L, "Runtime.getRuntime()", "0 0 * * * ?")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("格式");
        verify(applicationContext, never()).getBean(any(String.class));
    }
}
