package com.luminous.api.auth;

import com.luminous.api.common.BusinessException;
import org.springframework.http.HttpStatus;

/** 关注限频携带准确等待秒数，前端和其他调用方均可遵守。 */
public class FollowRateLimitException extends BusinessException {
    private final int retryAfterSeconds;
    public FollowRateLimitException(int retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS,"FOLLOW_RATE_LIMITED","操作有点快，请稍后再试");
        this.retryAfterSeconds = retryAfterSeconds;
    }
    public int getRetryAfterSeconds() { return retryAfterSeconds; }
}
