package com.marvel.framework.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CacheConfigTest {

    @Test
    void roundTripsStringSetWithTypeInformation() {
        GenericJackson2JsonRedisSerializer serializer = CacheConfig.buildValueSerializer();
        Set<String> value = new HashSet<>(List.of("*:*:*", "system:user:list"));

        Object restored = serializer.deserialize(serializer.serialize(value));

        assertThat(restored).isInstanceOf(Set.class);
        @SuppressWarnings("unchecked")
        Set<String> restoredSet = (Set<String>) restored;
        assertThat(restoredSet).containsExactlyInAnyOrder("*:*:*", "system:user:list");
    }

    @Test
    void roundTripsOwnDomainPojo() {
        GenericJackson2JsonRedisSerializer serializer = CacheConfig.buildValueSerializer();
        com.marvel.common.result.R<String> value = com.marvel.common.result.R.ok("hello");

        Object restored = serializer.deserialize(serializer.serialize(value));

        assertThat(restored).isInstanceOf(com.marvel.common.result.R.class);
        assertThat(((com.marvel.common.result.R<?>) restored).getData()).isEqualTo("hello");
    }

    @Test
    void rejectsNonAllowlistedPolymorphicType() {
        GenericJackson2JsonRedisSerializer serializer = CacheConfig.buildValueSerializer();
        // 典型反序列化 gadget 类名不在 java.util / java.lang 白名单内，必须被拒绝
        String payload = "[\"" + ClassPathXmlApplicationContext.class.getName() + "\",\"x\"]";

        assertThatThrownBy(() -> serializer.deserialize(payload.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(Exception.class);
    }
}
