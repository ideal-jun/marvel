package com.marvel.framework.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * RedisTemplate 配置：key 用 String 序列化、value 用 Jackson JSON 序列化，
 * 供验证码、登录防爆破计数等场景使用。
 *
 * <p>安全约束：<b>不</b>开启 Jackson 默认类型信息（default typing）。
 * 历史实现使用 LaissezFaireSubTypeValidator 开启多态反序列化，一旦 Redis 被写入
 * 恶意 payload，可触发反序列化 gadget 链导致远程代码执行（CWE-502）。
 * 本模板仅存放验证码答案与失败计数等 String/Number 值，无需多态能力。
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        Jackson2JsonRedisSerializer<Object> jsonSerializer =
                new Jackson2JsonRedisSerializer<>(om, Object.class);
        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        template.afterPropertiesSet();
        return template;
    }
}
