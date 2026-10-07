package com.luminous.api.security;

import com.luminous.api.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SlidingWindowRateLimiterTest {
    @Test void allowedAndDeniedComeFromAtomicScript() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(1L, 0L);
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(redis);
        assertThat(limiter.isAllowed("luminous:e2e:limit", 10, Duration.ofSeconds(60))).isTrue();
        assertThat(limiter.isAllowed("luminous:e2e:limit", 10, Duration.ofSeconds(60))).isFalse();
    }
    @Test void redisFailureIsExplicit503() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenThrow(new IllegalStateException("Redis 停机"));
        assertThatThrownBy(() -> new SlidingWindowRateLimiter(redis).isAllowed("luminous:e2e:limit", 10, Duration.ofSeconds(60)))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getStatus().value()).isEqualTo(503));
    }
    @Test void invalidParametersDoNotDisableProtection() {
        assertThatThrownBy(() -> new SlidingWindowRateLimiter(mock(StringRedisTemplate.class))
                .isAllowed("key", 0, Duration.ofSeconds(60))).isInstanceOf(IllegalArgumentException.class);
    }
}
