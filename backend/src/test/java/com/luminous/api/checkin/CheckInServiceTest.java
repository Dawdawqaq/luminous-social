package com.luminous.api.checkin;

import com.luminous.api.community.*;
import com.luminous.api.common.BusinessException;
import com.luminous.api.security.CurrentUser;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import java.time.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CheckInServiceTest {
    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> values;
    @Mock CommunityService communities;
    @Mock CommunityMemberMapper members;
    @Mock CheckInRepository repository;
    CheckInService service;
    CurrentUser user = new CurrentUser(100L, "test_user", "USER");
    LocalDate today = LocalDate.of(2026, 10, 1);
    @BeforeEach void setUp() {
        Community community = new Community(); community.setId(10L);
        lenient().when(communities.requireBySlug("design")).thenReturn(community);
        lenient().when(redis.opsForValue()).thenReturn(values);
        service = new CheckInService(redis, communities, members, repository,
                Clock.fixed(Instant.parse("2026-09-30T16:00:00Z"), ZoneId.of("Asia/Shanghai")));
    }
    @Test void nonMemberCannotCheckIn() {
        when(members.selectCount(any())).thenReturn(0L);
        assertThatThrownBy(() -> service.checkIn("design", user)).isInstanceOf(BusinessException.class);
        verify(repository, never()).insert(anyLong(), anyLong(), any());
    }
    @Test void firstCheckInSurvivesRedisFailureAndUsesShanghaiDate() {
        when(members.selectCount(any())).thenReturn(1L);
        when(repository.insert(10L, 100L, today)).thenReturn(true);
        when(repository.dates(10L, 100L, today, today)).thenReturn(List.of(today));
        when(repository.consecutiveDays(10L, 100L, today)).thenReturn(3);
        when(values.setBit(anyString(), anyLong(), eq(true))).thenThrow(new IllegalStateException("Redis 不可用"));
        var result = service.checkIn("design", user);
        assertThat(result.success()).isTrue();
        assertThat(result.totalDays()).isEqualTo(1);
        assertThat(result.consecutiveDays()).isEqualTo(3);
        verify(values).setBit(contains("202610"), eq(0L), eq(true));
    }
    @Test void duplicateReturnsRealStatistics() {
        when(members.selectCount(any())).thenReturn(1L);
        when(repository.insert(10L, 100L, today)).thenReturn(false);
        when(repository.dates(10L, 100L, today, today)).thenReturn(List.of(today));
        when(repository.consecutiveDays(10L, 100L, today)).thenReturn(31);
        var result = service.checkIn("design", user);
        assertThat(result.success()).isFalse();
        assertThat(result.totalDays()).isEqualTo(1);
        assertThat(result.consecutiveDays()).isEqualTo(31);
    }
    @Test void invalidMonthRejected() {
        assertThatThrownBy(() -> service.getStats("design", user, "2026-11")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.getStats("design", user, "2026-9")).isInstanceOf(BusinessException.class);
    }
}
