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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import com.luminous.api.mq.VoteMessage;
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

    @Mock
    private org.springframework.data.redis.core.HyperLogLogOperations<String, String> hyperLogLogOperations;

    @Mock
    private org.springframework.data.redis.core.ValueOperations<String, String> valueOperations;

    @Mock private com.luminous.api.vote.VoteCommandService voteCommandService;
    @Mock private com.luminous.api.vote.VoteProjectionService voteProjectionService;
    @Mock private com.luminous.api.vote.VoteRepository voteRepository;

    @Mock private com.luminous.api.cache.PostReadMetricsService readMetrics;

    @InjectMocks
    private PostService postService;

    private CurrentUser currentUser;
    private Post samplePost;
    private User sampleAuthor;
    private Community sampleCommunity;

    @BeforeEach
    void setUp() throws Exception {
        lenient().when(stringRedisTemplate.opsForHyperLogLog()).thenReturn(hyperLogLogOperations);
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
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
    void list_DeepPaginationReturnsExplicitWindowLimit() {
        assertThatThrownBy(() -> postService.list("hot", null, null, 51, 10, currentUser))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getCode()).isEqualTo("POST_WINDOW_LIMIT"));
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
        when(voteCommandService.accept(500L, 1L, 1)).thenReturn(new com.luminous.api.vote.VoteCommandService.Accepted(
                new VoteMessage(500L, 1L, 1, 1, 100L, "[E2E_TEST]-vote", 1), 1));
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

    @ParameterizedTest
    @CsvSource({"0,1,1", "1,1,0", "1,0,-1", "-1,0,1", "1,-1,-2", "-1,1,2", "0,-1,-1"})
    void votePost_UsesDurableStateAndDelta(int oldValue, int value, int delta) {
        when(voteCommandService.accept(500L, 1L, value)).thenReturn(new com.luminous.api.vote.VoteCommandService.Accepted(
                new VoteMessage(500L, 1L, value, delta, 100L, "[E2E_TEST]-vote", 1), delta));
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(sampleAuthor));
        when(communityService.mapByIds(any())).thenReturn(Map.of(10L, sampleCommunity));

        PostDtos.PostDto result = postService.votePost(500L, value, currentUser);

        ArgumentCaptor<VoteMessage> captor = ArgumentCaptor.forClass(VoteMessage.class);
        verify(voteMQProducer).sendVoteMessage(captor.capture());
        assertThat(captor.getValue().value()).isEqualTo(value);
        assertThat(captor.getValue().delta()).isEqualTo(delta);
        assertThat(result.userVote()).isEqualTo(value);
        assertThat(result.votes()).isEqualTo(delta);
    }

    @Test
    void votePost_CacheFailureDoesNotLoseAcceptedEvent() {
        VoteMessage message = new VoteMessage(500L, 1L, 1, 1, 100L, "[E2E_TEST]-cache-failure", 1);
        when(voteCommandService.accept(500L, 1L, 1)).thenReturn(new com.luminous.api.vote.VoteCommandService.Accepted(message, 1));
        when(voteProjectionService.refreshCommitted(500L)).thenThrow(new IllegalStateException("[E2E_TEST] Redis 不可用"));
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(sampleAuthor));
        when(communityService.mapByIds(any())).thenReturn(Map.of(10L, sampleCommunity));
        assertThat(postService.votePost(500L, 1, currentUser).userVote()).isEqualTo(1);
        verify(voteMQProducer).sendVoteMessage(message);
    }

    @Test
    void privatePost_RejectsAnonymousAndOtherUsersBeforeReadingOrWriting() {
        samplePost.setIsPublic(false);
        CurrentUser other = new CurrentUser(2L, "other", "USER");
        Comment comment = new Comment();
        comment.setId(600L);
        comment.setPostId(500L);
        when(commentMapper.selectById(600L)).thenReturn(comment);

        assertHidden(() -> postService.detail(500L, null));
        assertHidden(() -> postService.detail(500L, other));
        assertHidden(() -> postService.comments(500L, null));
        assertHidden(() -> postService.comments(500L, other));
        assertHidden(() -> postService.addComment(500L, new PostDtos.CreateCommentRequest("[E2E_TEST]"), other));
        assertHidden(() -> postService.votePost(500L, 1, other));
        assertHidden(() -> postService.voteComment(600L, 1, other));
        assertHidden(() -> postService.bookmark(500L, other));
        assertHidden(() -> postService.removeBookmark(500L, other));
        assertHidden(() -> postService.deletePost(500L, other));
        assertHidden(() -> postService.deleteComment(600L, other));
        assertHidden(() -> postService.toDto(samplePost, other));
        assertThat(postService.assemblePostDtos(List.of(samplePost), other)).isEmpty();
        verifyNoInteractions(voteCommandService, voteProjectionService, voteMQProducer, commentVoteMapper, bookmarkMapper);
        verify(commentMapper, never()).insert(any(Comment.class));
        verify(commentMapper, never()).selectList(any());
    }

    @Test
    void privatePost_AllowsAuthorAndAdministrator() {
        samplePost.setIsPublic(false);
        assertThat(postService.requirePost(500L, currentUser)).isSameAs(samplePost);
        assertThat(postService.requirePost(500L, new CurrentUser(2L, "admin", "ADMIN"))).isSameAs(samplePost);
    }

    @ParameterizedTest
    @CsvSource({"true", "false"})
    void create_OnlyPublicPostsAreFannedOut(boolean isPublic) {
        when(communityService.requireBySlug("tech")).thenReturn(sampleCommunity);
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(sampleAuthor));
        when(communityService.mapByIds(any())).thenReturn(Map.of(10L, sampleCommunity));
        postService.create(new PostDtos.CreatePostRequest("tech", "[E2E_TEST]", "内容", null, null, isPublic), currentUser);
        verify(feedService, times(isPublic ? 1 : 0)).fanOutToFollowers(eq(1L), anyLong());
    }

    private void assertHidden(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
