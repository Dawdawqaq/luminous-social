package com.luminous.api.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class TokenBlacklistService {
    private static final Logger log = LoggerFactory.getLogger(TokenBlacklistService.class);
    private static final String BAN_USER_KEY_PREFIX = "luminous:ban:user:";

    private final StringRedisTemplate redisTemplate;

    public TokenBlacklistService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 将被封禁用户写入 Redis 黑名单，默认保存 24 小时（与 JWT 有效期对齐）
     */
    public void banUser(Long userId) {
        banUser(userId, Duration.ofHours(24));
    }

    public void banUser(Long userId, Duration duration) {
        if (userId == null) return;
        try {
            redisTemplate.opsForValue().set(BAN_USER_KEY_PREFIX + userId, "banned", duration);
            log.info("Added user {} to Redis ban blacklist for {}", userId, duration);
        } catch (Exception e) {
            log.warn("Failed to write user ban to Redis: {}", e.getMessage());
        }
    }

    /**
     * 从 Redis 黑名单中移除解封用户
     */
    public void unbanUser(Long userId) {
        if (userId == null) return;
        try {
            redisTemplate.delete(BAN_USER_KEY_PREFIX + userId);
            log.info("Removed user {} from Redis ban blacklist", userId);
        } catch (Exception e) {
            log.warn("Failed to delete user ban from Redis: {}", e.getMessage());
        }
    }

    /**
     * 校验用户是否在实时封禁黑名单中
     */
    public boolean isUserBanned(Long userId) {
        if (userId == null) return false;
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(BAN_USER_KEY_PREFIX + userId));
        } catch (Exception e) {
            log.warn("Redis blacklist check failed, falling back to permissive: {}", e.getMessage());
            return false;
        }
    }
}
