package com.marvel.module.infra.jobs;

import com.marvel.common.annotation.JobTarget;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 演示任务 Bean：供「定时任务」管理页配置 invokeTarget 时使用（如 sampleJob.run）。
 * 实际业务任务按同样方式定义：托管为 Spring Bean，提供无参公开方法即可被调度调用。
 */
@Slf4j
@Component("sampleJob")
public class SampleJob {

    /** 演示方法：仅打印日志，用于验证调度链路 */
    @JobTarget
    public void run() {
        log.info("[sampleJob] 演示任务执行于 {}", java.time.LocalDateTime.now());
    }
}
