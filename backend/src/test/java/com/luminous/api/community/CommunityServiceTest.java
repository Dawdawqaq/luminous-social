package com.luminous.api.community;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luminous.api.auth.User;
import com.luminous.api.auth.UserMapper;
import com.luminous.api.common.BusinessException;
import com.luminous.api.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommunityServiceTest {

    @Mock
    private CommunityMapper communityMapper;

    @Mock
    private CommunityMemberMapper communityMemberMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private org.redisson.api.RedissonClient redissonClient;

    @Mock
    private org.redisson.api.RLock rLock;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private CommunityService communityService;

    private CurrentUser currentUser;
    private Community sampleCommunity;

    @BeforeEach
    void setUp() throws Exception {
        currentUser = new CurrentUser(1L, "felix", "USER");

        sampleCommunity = new Community();
        sampleCommunity.setId(10L);
        sampleCommunity.setSlug("design");
        sampleCommunity.setName("Design Community");
        sampleCommunity.setDescription("All about design");
        sampleCommunity.setMembersCount(5);
        sampleCommunity.setMembersUnit("members");
        sampleCommunity.setRulesJson("[\"Be respectful\"]");
        sampleCommunity.setModeratorsJson("[]");
        sampleCommunity.setOwnerId(1L);

        lenient().when(redissonClient.getLock(any(String.class))).thenReturn(rLock);
        lenient().when(rLock.tryLock(anyLong(), anyLong(), any())).thenReturn(true);
        lenient().when(rLock.isHeldByCurrentThread()).thenReturn(true);
    }

    @Test
    void join_UsesAtomicIncrement() {
        User owner = new User();
        owner.setId(1L);
        owner.setUsername("felix");
        owner.setDisplayName("Felix Maker");

        when(communityMapper.selectOne(any())).thenReturn(sampleCommunity);
        when(communityMemberMapper.selectCount(any())).thenReturn(0L).thenReturn(1L);
        when(userMapper.selectById(1L)).thenReturn(owner);

        CommunityDtos.CommunityDto result = communityService.join("design", currentUser);

        assertThat(result.joined()).isTrue();
        verify(communityMemberMapper).insert(any(CommunityMember.class));
        verify(communityMapper).updateMembersCountAtomic(10L, 1);
    }

    @Test
    void leave_UsesAtomicDecrement() {
        User owner = new User();
        owner.setId(1L);
        owner.setUsername("felix");
        owner.setDisplayName("Felix Maker");

        when(communityMapper.selectOne(any())).thenReturn(sampleCommunity);
        when(communityMemberMapper.delete(any())).thenReturn(1);
        when(userMapper.selectById(1L)).thenReturn(owner);
        when(communityMemberMapper.selectCount(any())).thenReturn(0L);

        CommunityDtos.CommunityDto result = communityService.leave("design", currentUser);

        verify(communityMapper).updateMembersCountAtomic(10L, -1);
    }

    @Test
    void create_ThrowsException_WhenSlugExists() {
        when(communityMapper.selectCount(any())).thenReturn(1L);

        CommunityDtos.CreateCommunityRequest request = new CommunityDtos.CreateCommunityRequest(
                "design", "Design Hub", "tech", "Description here"
        );

        assertThatThrownBy(() -> communityService.create(request, currentUser))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(be.getCode()).isEqualTo("SLUG_EXISTS");
                });
    }
}
