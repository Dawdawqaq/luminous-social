package com.luminous.api.feed;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.luminous.api.auth.UserFollow;
import com.luminous.api.auth.UserFollowMapper;
import com.luminous.api.post.Post;
import com.luminous.api.post.PostDtos;
import com.luminous.api.post.PostMapper;
import com.luminous.api.post.PostService;
import com.luminous.api.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeedServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @Mock
    private UserFollowMapper userFollowMapper;

    @Mock
    private PostMapper postMapper;

    @Mock
    private PostService postService;

    private FeedService feedService;
    private CurrentUser currentUser;

    @BeforeEach
    void setUp() {
        lenient().when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        feedService = new FeedService(stringRedisTemplate, userFollowMapper, postMapper, postService);
        currentUser = new CurrentUser(1L, "felix", "USER");
    }

    @Test
    void fanOutToFollowers_Skips_WhenBigAccountExceedsThreshold() {
        when(userFollowMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(15000L);

        feedService.fanOutToFollowers(100L, 999L);

        verify(userFollowMapper, never()).selectList(any());
        verify(stringRedisTemplate, never()).executePipelined(any(org.springframework.data.redis.core.RedisCallback.class));
    }

    @Test
    void fanOutToFollowers_ExecutesPipeline_WhenNormalAccount() {
        UserFollow follow = new UserFollow();
        follow.setFollowerId(200L);
        follow.setFollowingId(100L);

        when(userFollowMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(5L);
        when(userFollowMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(follow));

        feedService.fanOutToFollowers(100L, 999L);

        verify(stringRedisTemplate, times(1)).executePipelined(any(org.springframework.data.redis.core.RedisCallback.class));
    }

    @Test
    void scrollFeed_ReturnsEmpty_WhenNoTuplesInZSet() {
        when(zSetOperations.reverseRangeByScoreWithScores(anyString(), anyDouble(), anyDouble(), anyLong(), anyLong()))
                .thenReturn(Set.of());

        FeedDtos.ScrollResult<PostDtos.PostDto> result = feedService.scrollFeed(currentUser, null, 0, 10);

        assertThat(result.items()).isEmpty();
        assertThat(result.hasMore()).isFalse();
    }
}
