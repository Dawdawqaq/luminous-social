package com.luminous.api.checkin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.luminous.api.common.BusinessException;
import com.luminous.api.community.Community;
import com.luminous.api.community.CommunityMemberMapper;
import com.luminous.api.community.CommunityService;
import com.luminous.api.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CheckInServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private CommunityService communityService;

    @Mock
    private CommunityMemberMapper communityMemberMapper;

    private CheckInService checkInService;
    private CurrentUser currentUser;
    private Community sampleCommunity;

    @BeforeEach
    void setUp() {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        checkInService = new CheckInService(stringRedisTemplate, communityService, communityMemberMapper);
        currentUser = new CurrentUser(100L, "test_user", "USER");

        sampleCommunity = new Community();
        sampleCommunity.setId(10L);
        sampleCommunity.setSlug("design");
        sampleCommunity.setName("Design");
    }

    @Test
    void checkIn_Fails_WhenUserNotJoined() {
        when(communityService.requireBySlug("design")).thenReturn(sampleCommunity);
        when(communityMemberMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        assertThatThrownBy(() -> checkInService.checkIn("design", currentUser))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请先加入该社区后再签到");
    }

    @Test
    void checkIn_Succeeds_WhenJoinedAndFirstCheckIn() {
        when(communityService.requireBySlug("design")).thenReturn(sampleCommunity);
        when(communityMemberMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        when(valueOperations.getBit(anyString(), anyLong())).thenReturn(false);

        CheckInDtos.CheckInResponse response = checkInService.checkIn("design", currentUser);

        assertThat(response.success()).isTrue();
        verify(valueOperations).setBit(anyString(), anyLong(), eq(true));
        verify(stringRedisTemplate).expire(anyString(), eq(45L), eq(TimeUnit.DAYS));
    }

    @Test
    void checkIn_ReturnsAlreadyChecked_WhenBitAlreadySet() {
        when(communityService.requireBySlug("design")).thenReturn(sampleCommunity);
        when(communityMemberMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        when(valueOperations.getBit(anyString(), anyLong())).thenReturn(true);

        CheckInDtos.CheckInResponse response = checkInService.checkIn("design", currentUser);

        assertThat(response.success()).isFalse();
        assertThat(response.message()).isEqualTo("今日已签到");
        verify(valueOperations, never()).setBit(anyString(), anyLong(), eq(true));
    }
}
