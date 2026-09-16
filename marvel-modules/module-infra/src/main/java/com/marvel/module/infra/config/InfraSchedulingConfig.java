package com.marvel.module.infra.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * infra 域调度配置：为数据库可配置的定时任务提供调度线程池。
 * 拆分微服务时随 infra 模块整体迁移。
 */
@Configuration
@EnableScheduling
public class InfraSchedulingConfig {

    @Bean
    public ThreadPoolTaskScheduler jobTaskScheduler(
            @Value("${marvel.job.pool-size:4}") int poolSize) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(poolSize);
        scheduler.setThreadNamePrefix("marvel-job-");
        // 同一任务的上一次执行未结束时不再并发触发
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.initialize();
        return scheduler;
    }
}
