package com.luminous.api.feed;

import com.luminous.api.auth.*;
import com.luminous.api.post.*;
import com.luminous.api.common.BusinessException;
import com.luminous.api.security.CurrentUser;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.time.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeedServiceTest {
    @Mock StringRedisTemplate redis;
    @Mock UserFollowMapper follows;
    @Mock PostMapper posts;
    @Mock PostService assembler;
    FeedService service;
    CurrentUser user = new CurrentUser(1L, "test", "USER");
    @BeforeEach void setUp() { service = new FeedService(redis, follows, posts, assembler); }
    Post post(long id) {
        Post post = new Post(); post.setId(id); post.setAuthorId(100L); post.setIsPublic(true);
        post.setCreatedAt(LocalDateTime.of(2026, 10, 1, 10, 0)); return post;
    }
    @Test void bigAuthorDoesNotFanOut() {
        when(posts.selectById(999L)).thenReturn(post(999));
        when(follows.selectCount(any())).thenReturn(10001L);
        service.fanOutToFollowers(100L, 999L);
        verifyNoInteractions(redis);
        verify(follows, never()).selectList(any());
    }
    @Test void privatePostDoesNotFanOut() {
        Post post = post(999); post.setIsPublic(false);
        when(posts.selectById(999L)).thenReturn(post);
        service.fanOutToFollowers(100L, 999L);
        verifyNoInteractions(redis, follows);
    }
    @Test void sameTimestampAccumulatesOffsetAndHasMoreUsesLookahead() {
        Post first = post(3), second = post(2), extra = post(1);
        long score = first.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        when(posts.selectFollowedFeed(eq(1L), any(), any(), eq(4), eq(3))).thenReturn(List.of(first, second, extra));
        when(assembler.assemblePostDtos(List.of(first, second), user)).thenReturn(List.of());
        var result = service.scrollFeed(user, score, 4, 2);
        assertThat(result.offset()).isEqualTo(6);
        assertThat(result.minScore()).isEqualTo(score);
        assertThat(result.hasMore()).isTrue();
        verifyNoInteractions(redis);
    }
    @Test void emptyFeedEndsPagination() {
        when(posts.selectFollowedFeed(anyLong(), any(), any(), anyInt(), anyInt())).thenReturn(List.of());
        assertThat(service.scrollFeed(user, null, 0, 10).hasMore()).isFalse();
    }
    @Test void anonymousAndInvalidCursorRejected() {
        assertThatThrownBy(() -> service.scrollFeed(null, null, 0, 10)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.scrollFeed(user, null, -1, 10)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.scrollFeed(user, null, 0, 101)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(posts);
    }
}
