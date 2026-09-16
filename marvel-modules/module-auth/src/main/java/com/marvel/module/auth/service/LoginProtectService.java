package com.marvel.module.auth.service;

import com.marvel.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 登录防爆破保护：基于 Redis 统计「用户名+IP」维度的连续失败次数。
 *
 * <p>规则：连续失败达到 {@link #MAX_FAIL_COUNT} 次后锁定 {@link #LOCK_MINUTES} 分钟；
 * 登录成功即清零。使用 Redis 而非本地缓存，保证未来拆分微服务后多实例间计数一致。
 */
@Service
@RequiredArgsConstructor
public class LoginProtectService {

    /** 允许的最大连续失败次数 */
    private static final int MAX_FAIL_COUNT = 5;
    /** 锁定时长（分钟） */
    private static final long LOCK_MINUTES = 10;
    /** 失败计数的 Redis TTL，与锁定时长保持一致 */
    private static final Duration FAIL_TTL = Duration.ofMinutes(LOCK_MINUTES);

    private static final String FAIL_KEY_PREFIX = "marvel:login:fail:";

    /** 验证码接口每 IP 每分钟最大请求数，防止批量拉取耗尽 Redis */
    private static final int CAPTCHA_MAX_PER_MINUTE = 20;
    /** 登录接口每 IP 每分钟最大请求数，与「用户名+IP」锁定互补，防止轮换用户名爆破 */
    private static final int LOGIN_MAX_PER_MINUTE = 30;
    /** 限流窗口 */
    private static final Duration RATE_WINDOW = Duration.ofMinutes(1);
    private static final String RATE_KEY_PREFIX = "marvel:rate:";

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 校验账号当前是否处于锁定状态，锁定则直接拒绝登录。
     *
     * @param username 登录用户名
     * @param ip       客户端 IP
     */
    public void checkLocked(String username, String ip) {
        Integer fails = failCount(username, ip);
        if (fails != null && fails >= MAX_FAIL_COUNT) {
            throw new BusinessException("密码错误次数过多，账号已临时锁定，请" + LOCK_MINUTES + "分钟后再试");
        }
    }

    /** 登录失败：计数 +1，并刷新 TTL */
    public void recordFailure(String username, String ip) {
        String key = failKey(username, ip);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, FAIL_TTL);
        }
    }

    /** 登录成功：清除失败计数 */
    public void clearFailure(String username, String ip) {
        redisTemplate.delete(failKey(username, ip));
    }

    /** 验证码接口限流（按 IP，防止批量拉取验证码耗尽 Redis） */
    public void checkCaptchaRate(String ip) {
        checkRate("captcha", ip, CAPTCHA_MAX_PER_MINUTE);
    }

    /** 登录接口限流（按 IP，与「用户名+IP」锁定互补，防止轮换用户名爆破） */
    public void checkLoginRate(String ip) {
        checkRate("login", ip, LOGIN_MAX_PER_MINUTE);
    }

    private void checkRate(String action, String ip, int maxPerMinute) {
        String key = RATE_KEY_PREFIX + action + ":" + ip;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, RATE_WINDOW);
        }
        if (count != null && count > maxPerMinute) {
            throw new BusinessException("操作过于频繁，请稍后再试");
        }
    }

    private Integer failCount(String username, String ip) {
        Object value = redisTemplate.opsForValue().get(failKey(username, ip));
        return value == null ? null : Integer.valueOf(value.toString());
    }

    private String failKey(String username, String ip) {
        return FAIL_KEY_PREFIX + username + ":" + ip;
    }
}
