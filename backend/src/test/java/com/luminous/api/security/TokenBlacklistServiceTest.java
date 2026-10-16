package com.luminous.api.security;

import com.luminous.api.auth.*;
import com.luminous.api.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.*;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TokenBlacklistServiceTest {
    @Test void botIdentityIsRejectedByTokenAuthenticationEvenWhenNotBanned() {
        var users=mock(UserMapper.class);var bot=new User();bot.setAccountType("BOT");bot.setIsBanned(false);
        when(users.selectById(1L)).thenReturn(bot);
        assertThat(new TokenBlacklistService(mock(StringRedisTemplate.class),users,60).isUserBanned(1L)).isTrue();
    }
    @Test void configuredTokenLifetimeControlsHintCache() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class); ValueOperations<String,String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        new TokenBlacklistService(redis, mock(UserMapper.class), 4320).banUser(123L);
        verify(values).set("luminous:ban:user:123", "banned", Duration.ofDays(3));
    }
    @Test void missingOrBannedUsersAreDeniedRegardlessOfRedis() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class); UserMapper users = mock(UserMapper.class);
        TokenBlacklistService service = new TokenBlacklistService(redis, users, 60);
        assertThat(service.isUserBanned(123L)).isTrue();
        User user = new User(); user.setIsBanned(true); when(users.selectById(123L)).thenReturn(user);
        assertThat(service.isUserBanned(123L)).isTrue();
        user.setIsBanned(false); assertThat(service.isUserBanned(123L)).isFalse();
        verifyNoInteractions(redis);
    }
    @Test void databaseFailureNeverGrantsAccess() {
        UserMapper users = mock(UserMapper.class); when(users.selectById(123L)).thenThrow(new IllegalStateException("数据库不可用"));
        assertThatThrownBy(() -> new TokenBlacklistService(mock(StringRedisTemplate.class), users, 60).isUserBanned(123L))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getStatus().value()).isEqualTo(503));
    }
    @Test void unbanClearsHintCache() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        new TokenBlacklistService(redis, mock(UserMapper.class), 60).unbanUser(123L);
        verify(redis).delete("luminous:ban:user:123");
    }
}
