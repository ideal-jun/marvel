package com.marvel.module.system.config;

import com.marvel.module.system.listener.SseBroadcastSubscriber;
import com.marvel.module.system.service.SsePushService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * SSE 多节点扇出配置：订阅 Redis 广播频道。
 *
 * <p>连接工厂用 Boot 自动配置的 {@code RedisConnectionFactory}（与 Sa-Token 会话共用同一 Redis）。
 * 容器随应用启动建立订阅、随关闭解绑；Redis 短暂不可用时容器会自动重连。
 */
@Configuration
public class SseRedisConfig {

    @Bean
    public RedisMessageListenerContainer sseRedisMessageListenerContainer(RedisConnectionFactory connectionFactory,
                                                                         SseBroadcastSubscriber subscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, new ChannelTopic(SsePushService.BROADCAST_CHANNEL));
        return container;
    }
}
