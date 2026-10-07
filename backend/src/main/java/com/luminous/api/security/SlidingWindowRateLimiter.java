package com.luminous.api.security;

import com.luminous.api.common.BusinessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/** Redis 服务端时间和 Lua 原子窗口，清理、计数、写入及过期不可被并发穿透。 */
@Component
public class SlidingWindowRateLimiter {
    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>(
            "local t=redis.call('TIME'); local now=tonumber(t[1])*1000+math.floor(tonumber(t[2])/1000); "
            + "redis.call('ZREMRANGEBYSCORE',KEYS[1],'-inf',now-tonumber(ARGV[1])); "
            + "if redis.call('ZCARD',KEYS[1])>=tonumber(ARGV[2]) then return 0 end; "
            + "redis.call('ZADD',KEYS[1],now,ARGV[3]); redis.call('EXPIRE',KEYS[1],ARGV[4]); return 1", Long.class);
    private final StringRedisTemplate redis;
    public SlidingWindowRateLimiter(StringRedisTemplate redis) { this.redis = redis; }
    public boolean isAllowed(String key, int maximum, Duration window) {
        if (key == null || key.isBlank() || maximum < 1 || window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("限流参数无效");
        }
        try {
            Long result = redis.execute(SCRIPT, List.of(key), String.valueOf(window.toMillis()), String.valueOf(maximum),
                    UUID.randomUUID().toString(), String.valueOf(Math.max(1, window.toSeconds() + 1)));
            if (result == null) { throw new IllegalStateException("Redis 未返回限流结果"); }
            return result == 1;
        } catch (Exception ex) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "RATE_LIMIT_UNAVAILABLE", "安全验证服务暂不可用，请稍后重试");
        }
    }
}
