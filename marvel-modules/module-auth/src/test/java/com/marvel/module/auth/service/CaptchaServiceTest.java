package com.marvel.module.auth.service;

import com.marvel.common.constant.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CaptchaServiceTest {

    private RedisTemplate<String, Object> redis;
    private ValueOperations<String, Object> ops;
    private CaptchaService service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redis = mock(RedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        service = new CaptchaService(redis);
    }

    @Test
    void generateStoresAnswerWithTtlAndReturnsSvg() {
        Map<String, String> captcha = service.generateCaptcha();

        assertThat(captcha).containsKeys("uuid", "img");
        assertThat(captcha.get("uuid")).doesNotContain("-");
        assertThat(captcha.get("img")).contains("<svg").contains("= ?");
        verify(ops).set(startsWith(Constants.CAPTCHA_KEY_PREFIX), anyString(), eq(Duration.ofMinutes(2)));
    }

    @Test
    void verifyAcceptsCorrectAnswerAndConsumesIt() {
        when(ops.get(Constants.CAPTCHA_KEY_PREFIX + "abc")).thenReturn("5");

        assertThat(service.verify("abc", "5")).isTrue();
        // 一次性使用：无论对错，校验后立即删除
        verify(redis).delete(Constants.CAPTCHA_KEY_PREFIX + "abc");
    }

    @Test
    void verifyTrimsAnswerBeforeCompare() {
        when(ops.get(Constants.CAPTCHA_KEY_PREFIX + "abc")).thenReturn("5");
        assertThat(service.verify("abc", " 5 ")).isTrue();
    }

    @Test
    void verifyRejectsWrongAnswer() {
        when(ops.get(Constants.CAPTCHA_KEY_PREFIX + "abc")).thenReturn("5");
        assertThat(service.verify("abc", "6")).isFalse();
    }

    @Test
    void verifyRejectsMissingAnswerWithoutTouchingRedis() {
        when(ops.get(anyString())).thenReturn(null);
        assertThat(service.verify("abc", "5")).isFalse();
        verify(ops).get(Constants.CAPTCHA_KEY_PREFIX + "abc");
    }

    @Test
    void verifyRejectsBlankInputBeforeRedisAccess() {
        assertThat(service.verify(null, "5")).isFalse();
        assertThat(service.verify("abc", "")).isFalse();
        assertThat(service.verify(" ", "5")).isFalse();
        verifyNoInteractions(redis);
    }
}
