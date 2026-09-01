package com.luminous.api.post;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.luminous.api.auth.User;
import com.luminous.api.auth.UserMapper;
import com.luminous.api.common.BusinessException;
import com.luminous.api.common.PageResponse;
import com.luminous.api.community.Community;
import com.luminous.api.community.CommunityService;
import com.luminous.api.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private PostMapper postMapper;

    @Mock
    private CommentMapper commentMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private BookmarkMapper bookmarkMapper;

    @Mock
    private PostVoteMapper postVoteMapper;

    @Mock
    private CommentVoteMapper commentVoteMapper;

    @Mock
    private CommunityService communityService;

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @Mock
    private com.luminous.api.cache.PostCacheService postCacheService;

    @Mock
    private org.redisson.api.RedissonClient redissonClient;

    @Mock
    private org.redisson.api.RLock rLock;

    @Mock
    private com.luminous.api.cache.UserStatsCacheService userStatsCacheService;

    @Mock
    private com.luminous.api.feed.FeedService feedService;

    @Mock
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    @Mock
    private com.luminous.api.cache.RedisLuaScriptService redisLuaScriptService;

    @Mock
    private com.luminous.api.mq.VoteMQProducer voteMQProducer;

    @InjectMocks
    private PostService postService;

    private CurrentUser currentUser;
    private Post samplePost;
    private User sampleAuthor;
    private Community sampleCommunity;

    @BeforeEach
    void setUp() throws Exception {
        currentUser = new CurrentUser(1L, "felix", "USER");

        sampleAuthor = new User();
        sampleAuthor.setId(1L);
        sampleAuthor.setUsername("felix");
        sampleAuthor.setDisplayName("Felix Maker");

        sampleCommunity = new Community();
        sampleCommunity.setId(10L);
        sampleCommunity.setSlug("tech");
        sampleCommunity.setName("Technology");

        samplePost = new Post();
        samplePost.setId(500L);
        samplePost.setAuthorId(1L);
        samplePost.setCommunityId(10L);
        samplePost.setTitle("Sample Post");
        samplePost.setContent("Post content here");
        samplePost.setVotes(5);
        samplePost.setCommentsCount(2);
        samplePost.setIsPublic(true);
        samplePost.setCreatedAt(LocalDateTime.now());

        lenient().when(postCacheService.queryWithPassThrough(500L)).thenReturn(samplePost);
        lenient().when(redissonClient.getLock(any(String.class))).thenReturn(rLock);
        lenient().when(rLock.tryLock(anyLong(), anyLong(), any())).thenReturn(true);
        lenient().when(rLock.isHeldByCurrentThread()).thenReturn(true);
    }

    @Test
    void list_BatchLoadsAndAssemblesCorrectly() {
        IPage<Post> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(samplePost));

        when(postMapper.selectPage(any(), any())).thenReturn(page);
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(sampleAuthor));
        when(communityService.mapByIds(any())).thenReturn(Map.of(10L, sampleCommunity));
        when(postVoteMapper.selectList(any())).thenReturn(List.of());
        when(bookmarkMapper.selectList(any())).thenReturn(List.of());

        PageResponse<PostDtos.PostDto> response = postService.list("hot", null, null, 1, 10, currentUser);

        assertThat(response.items()).hasSize(1);
        PostDtos.PostDto item = response.items().get(0);
        assertThat(item.id()).isEqualTo("500");
        assertThat(item.title()).isEqualTo("Sample Post");
        assertThat(item.author().username()).isEqualTo("felix");
        assertThat(item.community().name()).isEqualTo("tech");
        assertThat(item.votes()).isEqualTo(5);

        // Verify batch queries were used instead of loop selects
        verify(userMapper, times(1)).selectBatchIds(any());
        verify(communityService, times(1)).mapByIds(any());
    }

    @Test
    void list_WithAuthorFilter_FiltersByAuthorId() {
        when(userMapper.selectOne(any())).thenReturn(sampleAuthor);
        IPage<Post> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(samplePost));

        when(postMapper.selectPage(any(), any())).thenReturn(page);
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(sampleAuthor));
        when(communityService.mapByIds(any())).thenReturn(Map.of(10L, sampleCommunity));
        when(postVoteMapper.selectList(any())).thenReturn(List.of());
        when(bookmarkMapper.selectList(any())).thenReturn(List.of());

        PageResponse<PostDtos.PostDto> response = postService.list("new", null, null, "felix", 1, 10, currentUser);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).author().username()).isEqualTo("felix");
    }

    @Test
    void list_DeepPaginationCircuitBreaker_ReturnsEmptyPage() {
        PageResponse<PostDtos.PostDto> response = postService.list("hot", null, null, 51, 10, currentUser);

        assertThat(response.items()).isEmpty();
        assertThat(response.total()).isEqualTo(0);
        verify(postMapper, never()).selectPage(any(), any());
    }

    @Test
    void list_SearchQueryLessThanTwoChars_ReturnsEmptyPage() {
        PageResponse<PostDtos.PostDto> response = postService.list("hot", null, "a", 1, 10, currentUser);

        assertThat(response.items()).isEmpty();
        assertThat(response.total()).isEqualTo(0);
        verify(postMapper, never()).selectPage(any(), any());
    }

    @Test
    void list_SearchQueryValid_AppliesFulltextSearch() {
        IPage<Post> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(samplePost));

        when(postMapper.selectPage(any(), any())).thenReturn(page);
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(sampleAuthor));
        when(communityService.mapByIds(any())).thenReturn(Map.of(10L, sampleCommunity));
        when(postVoteMapper.selectList(any())).thenReturn(List.of());
        when(bookmarkMapper.selectList(any())).thenReturn(List.of());

        PageResponse<PostDtos.PostDto> response = postService.list("hot", null, "java", 1, 10, currentUser);

        assertThat(response.items()).hasSize(1);
        verify(postMapper, times(1)).selectPage(any(), any());
    }

    @Test
    void addComment_UsesAtomicIncrement() {
        when(userMapper.selectById(1L)).thenReturn(sampleAuthor);

        PostDtos.CreateCommentRequest request = new PostDtos.CreateCommentRequest("Great post!");
        PostDtos.CommentDto commentDto = postService.addComment(500L, request, currentUser);

        assertThat(commentDto.content()).isEqualTo("Great post!");
        verify(commentMapper).insert(any(Comment.class));
        verify(postMapper).updateCommentsCountAtomic(500L, 1);
    }

    @Test
    void votePost_CalculatesDeltaAndExecutesAtomicUpdate() {
        when(redisLuaScriptService.executeVoteScript(eq(500L), eq(1L), eq(1)))
                .thenReturn(List.of(0L, 1L, 1L));
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(sampleAuthor));
        when(communityService.mapByIds(any())).thenReturn(Map.of(10L, sampleCommunity));

        PostDtos.PostDto result = postService.votePost(500L, 1, currentUser);

        assertThat(result.votes()).isEqualTo(1);
        verify(voteMQProducer).sendVoteMessage(any(com.luminous.api.mq.VoteMessage.class));
        verify(postCacheService).invalidatePost(500L);
    }

    @Test
    void deletePost_ThrowsForbidden_WhenNotAuthor() {
        CurrentUser otherUser = new CurrentUser(999L, "intruder", "USER");

        assertThatThrownBy(() -> postService.deletePost(500L, otherUser))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                });

        verify(postMapper, never()).deleteById(500L);
    }
}
