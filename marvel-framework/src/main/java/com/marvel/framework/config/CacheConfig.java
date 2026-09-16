package com.marvel.framework.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * 权限/角色缓存配置（Spring Cache + Redis）。
 *
 * <p>缓存用户角色与按钮权限，避免每次 {@code @SaCheckPermission} 都查库；变更接口
 * （用户-角色、角色-菜单、菜单权限）负责显式清除，另有 TTL 作为兜底。
 *
 * <p>序列化安全：使用 {@link GenericJackson2JsonRedisSerializer} 时必须自带 ObjectMapper，
 * 并把多态类型白名单限制在 {@code java.util.* / java.lang.*}，否则默认的宽松校验器会重新
 * 引入反序列化 gadget 风险（CWE-502）。
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** 权限缓存 TTL：正常情况下由变更接口立即失效，这里只作为兜底上界 */
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    public static final String CACHE_USER_ROLES = "user:roles";
    public static final String CACHE_USER_PERMS = "user:perms";
    public static final String CACHE_DICT_DATA = "dict:data";
    public static final String CACHE_CONFIG = "config";

    @Bean
    public RedisCacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(CACHE_TTL)
                .prefixCacheNameWith("marvel:cache:")
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(buildValueSerializer()));
        return RedisCacheManager.builder(connectionFactory).cacheDefaults(config).build();
    }

    /**
     * 缓存值序列化器：开启最小化的类型信息以正确还原 Set/List 等集合，仅允许
     * {@code java.util.*} 与 {@code java.lang.*} 子类型，其余一律拒绝。
     */
    static GenericJackson2JsonRedisSerializer buildValueSerializer() {
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        om.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                        .allowIfSubType("java.util.")
                        .allowIfSubType("java.lang.")
                        // 仅放行本项目自有 POJO（实体/返回体），不引入第三方 gadget
                        .allowIfSubType("com.marvel.")
                        .build(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);
        return new GenericJackson2JsonRedisSerializer(om);
    }
}
