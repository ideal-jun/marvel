package com.marvel.framework.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncConfigTest {

    @Test
    void logExecutorIsBoundedWithCallerRunsFallback() {
        ThreadPoolTaskExecutor executor = new AsyncConfig().marvelLogExecutor();

        assertThat(executor.getCorePoolSize()).isEqualTo(2);
        assertThat(executor.getMaxPoolSize()).isEqualTo(4);
        assertThat(executor.getThreadNamePrefix()).isEqualTo("marvel-log-");
        assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity()).isEqualTo(1000);
        assertThat(executor.getThreadPoolExecutor().getRejectedExecutionHandler())
                .isInstanceOf(ThreadPoolExecutor.CallerRunsPolicy.class);

        executor.shutdown();
    }
}
