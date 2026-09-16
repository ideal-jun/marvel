package com.marvel.module.system.listener;

import com.marvel.api.system.event.LoginRecordEvent;
import com.marvel.api.system.event.OperLogEvent;
import com.marvel.framework.config.AsyncConfig;
import com.marvel.module.system.entity.SysLogininfor;
import com.marvel.module.system.entity.SysOperLog;
import com.marvel.module.system.service.SysLogininforService;
import com.marvel.module.system.service.SysOperLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 日志事件监听：framework/auth 模块发布的事件在此落库（system 域的表）。
 * 微服务化时以 MQ 消费者替换本类即可。
 *
 * <p>监听方法通过 {@code @Async} 在专用线程池执行，落库不再占用请求线程；
 * 这里仍必须吞掉所有落库异常——审计日志绝不能影响主流程。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LogEventListener {

    private final SysOperLogService operLogService;
    private final SysLogininforService logininforService;

    @Async(AsyncConfig.LOG_EXECUTOR)
    @EventListener
    public void onOperLog(OperLogEvent event) {
        try {
            SysOperLog entity = new SysOperLog();
            entity.setTitle(event.title());
            entity.setMethod(event.method());
            entity.setRequestMethod(event.requestMethod());
            entity.setOperUrl(event.operUrl());
            entity.setOperParam(event.operParam());
            entity.setOperUser(event.operUser());
            entity.setOperIp(event.operIp());
            entity.setStatus(event.status());
            entity.setErrorMsg(event.errorMsg());
            operLogService.save(entity);
        } catch (Exception e) {
            log.warn("操作日志落库失败: {}", e.getMessage());
        }
    }

    @Async(AsyncConfig.LOG_EXECUTOR)
    @EventListener
    public void onLoginRecord(LoginRecordEvent event) {
        try {
            SysLogininfor entity = new SysLogininfor();
            entity.setUsername(event.username());
            entity.setIpaddr(event.ipaddr());
            entity.setBrowser(event.browser());
            entity.setOs(event.os());
            entity.setMsg(event.msg());
            entity.setStatus(event.status());
            logininforService.save(entity);
        } catch (Exception e) {
            log.warn("登录日志落库失败: {}", e.getMessage());
        }
    }
}
