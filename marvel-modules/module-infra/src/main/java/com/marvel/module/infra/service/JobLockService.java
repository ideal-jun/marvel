package com.marvel.module.infra.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

/**
 * 定时任务分布式锁：多实例部署时保证同一任务同一时刻只在一个实例执行。
 *
 * <p>Redis {@code SET NX EX} 加锁，Lua 脚本按 token 比对释放，避免误删其他实例持有的锁；
 * TTL 作为实例宕机时的兜底释放。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobLockService {

    private static final Duration LOCK_TTL = Duration.ofMinutes(10);
    private static final String LOCK_KEY_PREFIX = "marvel:job:lock:";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate stringRedisTemplate;

    /** 尝试加锁：成功返回持有者 token，已被占用返回 null */
    public String tryLock(Long jobId) {
        String token = UUID.randomUUID().toString();
        Boolean acquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(LOCK_KEY_PREFIX + jobId, token, LOCK_TTL);
        return Boolean.TRUE.equals(acquired) ? token : null;
    }

    /** 释放锁：仅当持有者与 token 一致时删除 */
    public void unlock(Long jobId, String token) {
        if (token == null) {
            return;
        }
        try {
            stringRedisTemplate.execute(UNLOCK_SCRIPT,
                    Collections.singletonList(LOCK_KEY_PREFIX + jobId), token);
        } catch (Exception e) {
            log.warn("释放任务锁失败 jobId={}: {}", jobId, e.getMessage());
        }
    }
}
