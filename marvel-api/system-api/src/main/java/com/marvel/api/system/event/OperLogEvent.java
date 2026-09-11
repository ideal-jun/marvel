package com.marvel.api.system.event;

/**
 * 操作日志事件：framework 的切面发布，system 模块监听后落库 sys_oper_log。
 * 微服务化时将事件替换为 MQ 消息即可，两边代码不变。
 */
public record OperLogEvent(
        String title,
        String method,
        String requestMethod,
        String operUrl,
        String operParam,
        String operUser,
        String operIp,
        /** 0=成功 1=失败 */
        String status,
        String errorMsg
) {
}
