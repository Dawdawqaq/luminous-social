package com.luminous.api.security;

import com.luminous.api.auth.User;
import com.luminous.api.auth.UserMapper;
import com.luminous.api.common.AfterCommit;
import com.luminous.api.common.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.time.Duration;

/** 永久封禁以用户表为准；Redis 只是与 Token 有效期匹配的提示缓存，不能授予访问权限。 */
@Service
public class TokenBlacklistService {
    private static final Logger log = LoggerFactory.getLogger(TokenBlacklistService.class);
    private static final String PREFIX = "luminous:ban:user:";
    private final StringRedisTemplate redis;
    private final UserMapper users;
    private final Duration tokenLifetime;
    public TokenBlacklistService(StringRedisTemplate redis, UserMapper users,
            @Value("${app.jwt.expiration-minutes:1440}") long expirationMinutes) {
        if (expirationMinutes < 1) { throw new IllegalArgumentException("Token 有效期必须为正数"); }
        this.redis = redis; this.users = users; this.tokenLifetime = Duration.ofMinutes(expirationMinutes);
    }
    public void banUser(Long userId) { banUser(userId, tokenLifetime); }
    public void banUser(Long userId, Duration duration) {
        if (userId == null) { return; }
        AfterCommit.run(() -> {
            try { redis.opsForValue().set(PREFIX + userId, "banned", duration); }
            catch (Exception ex) { log.warn("封禁提示缓存写入失败，数据库仍保证封禁", ex); }
        });
    }
    public void unbanUser(Long userId) {
        if (userId == null) { return; }
        AfterCommit.run(() -> {
            try { redis.delete(PREFIX + userId); }
            catch (Exception ex) { log.warn("解封提示缓存清理失败，数据库仍决定状态", ex); }
        });
    }
    public boolean isUserBanned(Long userId) {
        if (userId == null) { return true; }
        try {
            User user = users.selectById(userId);
            return user == null || Boolean.TRUE.equals(user.getIsBanned());
        } catch (Exception ex) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_STATE_UNAVAILABLE", "暂时无法确认账号状态，请稍后重试");
        }
    }
}
