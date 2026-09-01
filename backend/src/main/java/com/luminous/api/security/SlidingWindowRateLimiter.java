package com.luminous.api.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * 基于 Redis SortedSet 的分布式滑动时间窗口限流器 (Sliding Window Rate Limiter)
 * <p>
 * 原理：
 * 1. 使用 ZSet 存储请求的时间戳序列，Score 为时间戳 (ms)，Member 为唯一请求标识 (timestamp + uuid)。
 * 2. 每次请求到达时，清除 [0, now - windowSize] 范围内的过期请求记录 (ZREMRANGEBYSCORE)。
 * 3. 统计当前滑动窗口内的有效请求总数 (ZCARD)。
 * 4. 若未超限，则将当前请求插入 ZSet (ZADD) 并放行；若超限则拦截。
 * 5. 自动刷新 Key 的 TTL，防止冷数据常驻内存。
 */
@Component
public class SlidingWindowRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(SlidingWindowRateLimiter.class);

    private final StringRedisTemplate redisTemplate;

    public SlidingWindowRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 校验在指定滑动时间窗口内是否允许请求通过
     *
     * @param key         限流 Key (例如 IP 或 用户ID)
     * @param maxRequests 窗口期内允许的最大请求数
     * @param window      滑动窗口时长
     * @return true - 允许通过; false - 触发限流
     */
    public boolean isAllowed(String key, int maxRequests, Duration window) {
        if (key == null || maxRequests <= 0 || window == null) {
            return true;
        }

        long now = System.currentTimeMillis();
        long windowStart = now - window.toMillis();
        String member = now + ":" + UUID.randomUUID().toString().substring(0, 8);

        try {
            // 1. 移除窗口时间线之前的过期成员
            redisTemplate.opsForZSet().removeRangeByScore(key, 0, (double) windowStart);

            // 2. 统计当前窗口内的请求数
            Long count = redisTemplate.opsForZSet().zCard(key);
            long currentCount = count == null ? 0 : count;

            if (currentCount >= maxRequests) {
                log.warn("Rate limit exceeded for key: [{}], current requests in window: {}, max allowed: {}", key, currentCount, maxRequests);
                return false;
            }

            // 3. 记录本次请求
            redisTemplate.opsForZSet().add(key, member, (double) now);

            // 4. 设置过期时间（略大于窗口时长，使用秒级以避免 Spring Data Redis 3.5 pExpire 递归 bug）
            long expireSecs = Math.max(1, window.toSeconds() + 5);
            redisTemplate.expire(key, expireSecs, java.util.concurrent.TimeUnit.SECONDS);

            return true;
        } catch (Exception e) {
            log.warn("Sliding window rate limit check failed for key [{}], failing open gracefully: {}", key, e.getMessage());
            return true;
        }
    }
}
