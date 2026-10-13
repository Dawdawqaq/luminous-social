package com.luminous.api.post;

import com.luminous.api.common.BusinessException;
import org.springframework.http.HttpStatus;

/** 将服务端等待时间传给前端，限流不清空草稿。 */
public final class CommentRateLimitException extends BusinessException {
    private final int retryAfterSeconds;

    public CommentRateLimitException(int retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, "COMMENT_RATE_LIMITED",
                "评论发布太频繁，请 " + retryAfterSeconds + " 秒后重试。");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int getRetryAfterSeconds() { return retryAfterSeconds; }
}
