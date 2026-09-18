package com.marvel.module.system.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 长连接管理与事件推送。
 *
 * <p>用于服务端主动向在线浏览器推送事件（当前场景：新公告发布即点亮未读角标）。
 * 连接以 {@code Authorization} 头鉴权（由全局 Sa-Token 拦截器在建立时校验一次），
 * 存活由 30s 心跳与客户端断线重连共同维持。
 *
 * <p><b>多节点</b>：连接表是本进程内存结构，故对外只暴露 {@link #broadcast}——
 * 事件经 Redis Pub/Sub 扇出，各节点订阅后推给本机连接，任一节点发布全集群可见；
 * 发布节点自身也通过订阅回调投递，不额外直推，保证每条事件每节点恰好投递一次。
 */
public interface SsePushService {

    /** 广播事件使用的 Redis 频道（发布端与订阅端共用） */
    String BROADCAST_CHANNEL = "marvel:sse:broadcast";

    /**
     * 为用户建立一条 SSE 连接。
     *
     * <p>同一账号允许多条连接（多标签页各占一条）；连接断开（完成/超时/异常）时自动清理。
     *
     * @param userId 登录用户 ID
     * @return 已注册的 emitter（立即下发 connected 事件便于客户端确认链路可用）
     */
    SseEmitter subscribe(long userId);

    /**
     * 广播事件到全集群（多节点安全的对外入口）。
     *
     * <p>发布失败（如 Redis 不可用）只记日志不外抛——推送是旁路能力，
     * 不能影响公告发布等主业务。
     *
     * @param event 事件名（客户端按事件名分发处理）
     * @param data  负载，按 JSON 序列化
     */
    void broadcast(String event, Object data);

    /**
     * 推送给本进程内的全部连接（由 Redis 订阅端调用，业务代码请用 {@link #broadcast}）。
     *
     * @param event 事件名
     * @param data  负载
     */
    void pushToLocal(String event, Object data);
}
