package com.marvel.module.system.listener;

import com.marvel.module.system.service.SsePushService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.DefaultMessage;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class SseBroadcastSubscriberTest {

    private final SsePushService pushService = mock(SsePushService.class);
    private final SseBroadcastSubscriber subscriber = new SseBroadcastSubscriber(JsonMapper.builder().build(), pushService);

    private DefaultMessage message(String payload) {
        return new DefaultMessage(SsePushService.BROADCAST_CHANNEL.getBytes(StandardCharsets.UTF_8),
                payload.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void 解析广播消息并投递给本机连接() {
        subscriber.onMessage(message("""
                {"event":"notice","data":{"noticeId":9,"title":"维护通知"}}
                """), null);

        verify(pushService).pushToLocal(eq("notice"), any(JsonNode.class));
    }

    @Test
    void 缺少_event_字段的消息被忽略() {
        subscriber.onMessage(message("""
                {"data":{"noticeId":9}}
                """), null);

        verify(pushService, never()).pushToLocal(any(), any());
    }

    @Test
    void 脏数据只记日志不抛异常_避免中断后续订阅() {
        assertThatCode(() -> subscriber.onMessage(message("not-a-json"), null)).doesNotThrowAnyException();
        assertThatCode(() -> subscriber.onMessage(message(""), null)).doesNotThrowAnyException();
    }
}
