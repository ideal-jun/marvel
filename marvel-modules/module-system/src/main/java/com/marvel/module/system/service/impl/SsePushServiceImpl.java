package com.marvel.module.system.service.impl;

import com.marvel.module.system.service.SsePushService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * SSE 连接管理与推送实现。
 *
 * <p>连接不设服务端超时（timeout=0），由 30s 心跳防止中间代理空闲断连，
 * 断连后的恢复交给客户端重连；发送失败的连接即时剔除。
 *
 * <p>跨节点投递：{@link #broadcast} 只做一件事——把事件发到 Redis 频道，
 * 当前节点与其余节点一样通过订阅回调 {@link #pushToLocal} 投递，
 * 因此不存在"发布节点重复推送"的问题。
 */
@Service
public class SsePushServiceImpl implements SsePushService {

    private static final Logger log = LoggerFactory.getLogger(SsePushServiceImpl.class);

    /** userId → 该用户的全部在线连接（同账号多标签页各占一条），仅本进程可见 */
    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    private final StringRedisTemplate redisTemplate;

    /**
     * 负载序列化用 Boot 自动配置的 Jackson 3 映射器（{@code tools.jackson}，Boot 4 起 HTTP 序列化同源），
     * 故推送里的时间等字段格式与接口返回天然一致，无需另行配置 ObjectMapper。
     */
    private final JsonMapper jsonMapper;

    /** 心跳调度线程：daemon，进程退出不阻塞 */
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "sse-heartbeat");
        t.setDaemon(true);
        return t;
    });

    public SsePushServiceImpl(StringRedisTemplate redisTemplate, JsonMapper jsonMapper) {
        this.redisTemplate = redisTemplate;
        this.jsonMapper = jsonMapper;
        heartbeat.scheduleAtFixedRate(this::sendHeartbeat, 30, 30, TimeUnit.SECONDS);
    }

    @Override
    public SseEmitter subscribe(long userId) {
        SseEmitter emitter = new SseEmitter(0L);
        List<SseEmitter> list = emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError(e -> remove(userId, emitter));
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException e) {
            remove(userId, emitter);
        }
        return emitter;
    }

    @Override
    public void broadcast(String event, Object data) {
        try {
            String payload = jsonMapper.writeValueAsString(Map.of("event", event, "data", data));
            redisTemplate.convertAndSend(BROADCAST_CHANNEL, payload);
        } catch (Exception e) {
            // 推送为旁路能力：Redis 不可用也不能影响公告发布等主业务
            log.warn("SSE 广播失败（不影响主业务）event={}: {}", event, e.getMessage());
        }
    }

    @Override
    public void pushToLocal(String event, Object data) {
        emitters.forEach((userId, list) -> list.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
            } catch (Exception e) {
                // 连接已失效（客户端关闭/网络中断）：剔除，等客户端重连
                log.debug("SSE 推送失败，移除连接 userId={}: {}", userId, e.getMessage());
                remove(userId, emitter);
            }
        }));
    }

    private void sendHeartbeat() {
        emitters.forEach((userId, list) -> list.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().name("heartbeat").data(System.currentTimeMillis()));
            } catch (Exception e) {
                remove(userId, emitter);
            }
        }));
    }

    private void remove(long userId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(userId);
        if (list == null) {
            return;
        }
        list.remove(emitter);
        if (list.isEmpty()) {
            emitters.remove(userId, list);
        }
    }

    @PreDestroy
    void shutdown() {
        heartbeat.shutdownNow();
        emitters.values().forEach(list -> list.forEach(SseEmitter::complete));
    }
}
