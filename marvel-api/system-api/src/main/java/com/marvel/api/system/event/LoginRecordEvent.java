package com.marvel.api.system.event;

/**
 * 登录日志事件：auth 模块在登录成功/失败时发布，system 模块监听落库 sys_logininfor。
 */
public record LoginRecordEvent(
        String username,
        String ipaddr,
        String browser,
        String os,
        String msg,
        /** 0=成功 1=失败 */
        String status
) {
}
