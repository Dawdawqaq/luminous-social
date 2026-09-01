package com.luminous.api.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SlidingWindowRateLimiterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    private SlidingWindowRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        rateLimiter = new SlidingWindowRateLimiter(redisTemplate);
    }

    @Test
    void isAllowed_ReturnsTrue_WhenUnderLimit() {
        when(zSetOperations.zCard(eq("test_key"))).thenReturn(5L);

        boolean allowed = rateLimiter.isAllowed("test_key", 10, Duration.ofSeconds(60));

        assertThat(allowed).isTrue();
        verify(zSetOperations).removeRangeByScore(eq("test_key"), eq(0.0), anyDouble());
        verify(zSetOperations).add(eq("test_key"), anyString(), anyDouble());
        verify(redisTemplate).expire(eq("test_key"), anyLong(), eq(java.util.concurrent.TimeUnit.SECONDS));
    }

    @Test
    void isAllowed_ReturnsFalse_WhenExceedsLimit() {
        when(zSetOperations.zCard(eq("test_key"))).thenReturn(10L);

        boolean allowed = rateLimiter.isAllowed("test_key", 10, Duration.ofSeconds(60));

        assertThat(allowed).isFalse();
        verify(zSetOperations, never()).add(eq("test_key"), anyString(), anyDouble());
    }

    @Test
    void isAllowed_FailsOpenGracefully_OnRedisException() {
        when(zSetOperations.removeRangeByScore(any(), anyDouble(), anyDouble()))
                .thenThrow(new RuntimeException("Redis connection error"));

        boolean allowed = rateLimiter.isAllowed("test_key", 10, Duration.ofSeconds(60));

        assertThat(allowed).isTrue();
    }
}
