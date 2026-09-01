package com.luminous.api.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenBlacklistServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private TokenBlacklistService tokenBlacklistService;

    @Test
    void banUser_SetsKeyInRedis() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        tokenBlacklistService.banUser(123L, Duration.ofHours(1));

        verify(valueOperations).set(eq("luminous:ban:user:123"), eq("banned"), eq(Duration.ofHours(1)));
    }

    @Test
    void unbanUser_DeletesKeyInRedis() {
        tokenBlacklistService.unbanUser(123L);

        verify(redisTemplate).delete("luminous:ban:user:123");
    }

    @Test
    void isUserBanned_ReturnsTrue_WhenKeyExists() {
        when(redisTemplate.hasKey("luminous:ban:user:123")).thenReturn(true);

        boolean banned = tokenBlacklistService.isUserBanned(123L);

        assertThat(banned).isTrue();
    }

    @Test
    void isUserBanned_ReturnsFalse_WhenKeyDoesNotExist() {
        when(redisTemplate.hasKey("luminous:ban:user:123")).thenReturn(false);

        boolean banned = tokenBlacklistService.isUserBanned(123L);

        assertThat(banned).isFalse();
    }
}
