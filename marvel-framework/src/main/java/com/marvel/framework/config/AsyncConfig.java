package com.marvel.framework.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 审计日志异步执行配置：操作日志 / 登录日志落库从请求线程剥离，避免每个写操作都被一次
 * 同步 insert 拖慢。
 *
 * <p>可靠性取舍：
 * <ul>
 *   <li>队列满时使用 {@link CallerRunsPolicy} 回退到调用线程执行，保证审计日志不丢；</li>
 *   <li>关闭时等待 <b>10s</b> 让在途日志落库，避免停机丢日志。</li>
 * </ul>
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /** 审计日志专用执行器 Bean 名称 */
    public static final String LOG_EXECUTOR = "marvelLogExecutor";

    @Bean(name = LOG_EXECUTOR)
    public ThreadPoolTaskExecutor marvelLogExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("marvel-log-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
