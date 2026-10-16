package com.luminous.api.post;

import com.luminous.api.common.BusinessException;
import org.springframework.http.HttpStatus;

/** 传递实际等待时间，客户端保留草稿并在按钮上倒计时。 */
public final class PostRateLimitException extends BusinessException {
    private final int retryAfterSeconds;
    public PostRateLimitException(int seconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, "POST_RATE_LIMITED", "发布有点频繁，请稍后再试");
        retryAfterSeconds = seconds;
    }
    public int getRetryAfterSeconds() { return retryAfterSeconds; }
}
