package com.marvel.module.system.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.marvel.module.system.service.SsePushService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 订阅端点。
 *
 * <p>建立连接时由全局 Sa-Token 拦截器完成一次登录校权（Authorization 头），
 * 之后事件经长连接下发；注意连接本身不再随请求周期鉴权，
 * token 失效由客户端收到 401 后停止重连兜底。
 */
@RestController
@RequiredArgsConstructor
public class SseController {

    private final SsePushService ssePushService;

    @GetMapping(value = "/sse/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe() {
        return ssePushService.subscribe(StpUtil.getLoginIdAsLong());
    }
}
