package com.marvel.module.auth.service;

import com.marvel.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginProtectServiceTest {

    private RedisTemplate<String, Object> redis;
    private ValueOperations<String, Object> ops;
    private LoginProtectService service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redis = mock(RedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        service = new LoginProtectService(redis);
    }

    @Test
    void lockedWhenFailureCountReachesThreshold() {
        when(ops.get("marvel:login:fail:bob:1.1.1.1")).thenReturn(5);
        assertThatThrownBy(() -> service.checkLocked("bob", "1.1.1.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("锁定");
    }

    @Test
    void notLockedBelowThreshold() {
        when(ops.get(anyString())).thenReturn(4);
        assertThatCode(() -> service.checkLocked("bob", "1.1.1.1")).doesNotThrowAnyException();
    }

    @Test
    void recordFailureSetsTtlOnFirstFailure() {
        when(ops.increment(anyString())).thenReturn(1L);
        service.recordFailure("bob", "1.1.1.1");

        verify(ops).increment("marvel:login:fail:bob:1.1.1.1");
        verify(redis).expire(eq("marvel:login:fail:bob:1.1.1.1"), any(java.time.Duration.class));
    }

    @Test
    void clearFailureDeletesCounter() {
        service.clearFailure("bob", "1.1.1.1");
        verify(redis).delete("marvel:login:fail:bob:1.1.1.1");
    }

    @Test
    void captchaRateLimitBlocksAboveThreshold() {
        when(ops.increment(anyString())).thenReturn(21L);
        assertThatThrownBy(() -> service.checkCaptchaRate("1.1.1.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("频繁");
    }

    @Test
    void captchaRateLimitAllowsAtThreshold() {
        when(ops.increment(anyString())).thenReturn(20L);
        assertThatCode(() -> service.checkCaptchaRate("1.1.1.1")).doesNotThrowAnyException();
    }

    @Test
    void loginRateLimitBlocksAboveThreshold() {
        when(ops.increment(anyString())).thenReturn(31L);
        assertThatThrownBy(() -> service.checkLoginRate("1.1.1.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("频繁");
    }

    @Test
    void loginRateLimitAllowsAtThreshold() {
        when(ops.increment(anyString())).thenReturn(30L);
        assertThatCode(() -> service.checkLoginRate("1.1.1.1")).doesNotThrowAnyException();
    }
}
