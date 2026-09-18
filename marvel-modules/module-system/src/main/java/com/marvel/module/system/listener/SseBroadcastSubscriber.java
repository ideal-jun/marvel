package com.marvel.module.system.listener;

import com.marvel.module.system.service.SsePushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * SSE 广播订阅端：接收 {@link SsePushService#BROADCAST_CHANNEL} 上的事件，
 * 投递给本进程持有的全部 SSE 连接。
 *
 * <p>每个节点都会收到同一条消息（Redis Pub/Sub 广播语义），
 * 从而实现"任一节点发布、全集群在线用户可见"。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SseBroadcastSubscriber implements MessageListener {

    /** Boot 自动配置的 Jackson 3 映射器（与发布端同源） */
    private final JsonMapper jsonMapper;

    private final SsePushService ssePushService;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String body = new String(message.getBody());
        try {
            JsonNode node = jsonMapper.readTree(body);
            String event = node.path("event").asText();
            if (event.isEmpty()) {
                log.warn("SSE 广播消息缺少 event 字段，已忽略: {}", body);
                return;
            }
            // 传 JsonNode 而非字符串：SSE 写出时按 JSON 原样序列化，客户端可直接 JSON.parse
            ssePushService.pushToLocal(event, node.path("data"));
        } catch (Exception e) {
            // 单条消息异常不能中断订阅（否则后续事件全部收不到）
            log.warn("SSE 广播消息解析失败，已忽略: {}", e.getMessage());
        }
    }
}
