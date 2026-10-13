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
    @Mock private com.luminous.api.growth.GrowthQueryService growthQueries;
    @Mock private com.luminous.api.upload.UploadService uploadService;
    @Mock private CommentPublicationPolicy commentPublicationPolicy;

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
        lenient().when(postMapper.selectForUpdate(500L)).thenReturn(samplePost);
        lenient().when(commentMapper.selectForUpdate(any())).thenAnswer(invocation -> commentMapper.selectById((Long) invocation.getArgument(0)));
        lenient().when(commentVoteMapper.selectForUpdate(any(), any())).thenAnswer(invocation -> {
            // 模拟唯一键初始化后可当前读的零状态；图片和回复投票用例继续验证原返回内容。
            CommentVote vote = new CommentVote();
            vote.setId(900L);
            vote.setCommentId(invocation.getArgument(0));
            vote.setUserId(invocation.getArgument(1));
            vote.setValue(0);
            return vote;
        });
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
        verify(eventPublisher, never()).publishEvent(any(com.luminous.api.notification.NotificationEvent.class));
        verify(eventPublisher).publishEvent(any(com.luminous.api.growth.GrowthEvent.Created.class));
    }

    @Test
    void addComment_NotifiesPostAuthorWithBoundedPreview() {
        CurrentUser commenter = new CurrentUser(2L, "e2e_commenter", "USER");
        when(userMapper.selectById(2L)).thenReturn(sampleAuthor);
        String content = "[E2E_TEST] " + "评论".repeat(140);

        postService.addComment(500L, new PostDtos.CreateCommentRequest(content), commenter);

        ArgumentCaptor<com.luminous.api.notification.NotificationEvent> event =
                ArgumentCaptor.forClass(com.luminous.api.notification.NotificationEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().actorId()).isEqualTo(2L);
        assertThat(event.getValue().receiverId()).isEqualTo(1L);
        assertThat(event.getValue().type()).isEqualTo("COMMENT_POST");
        assertThat(event.getValue().targetType()).isEqualTo("POST");
        assertThat(event.getValue().targetId()).isEqualTo(500L);
        assertThat(event.getValue().contentPreview()).isEqualTo(content.substring(0, 255));
        assertThat(event.getValue().eventId()).isNotBlank();
    }

    @Test
    void imageOnlyComment_PersistsImageAndPreservesItInListAndVoteResponse() {
        CurrentUser commenter = new CurrentUser(2L, "E2E_TEST_image", "USER");
        String image = "/api/files/test.png?token=test";
        when(uploadService.requireOwnedImage(image, commenter)).thenReturn(image);
        when(userMapper.selectById(2L)).thenReturn(sampleAuthor);
        PostDtos.CommentDto created = postService.addComment(500L, new PostDtos.CreateCommentRequest(null, image), commenter);
        ArgumentCaptor<Comment> saved = ArgumentCaptor.forClass(Comment.class);
        verify(commentMapper).insert(saved.capture());
        Comment comment = saved.getValue();
        assertThat(comment.getContent()).isEmpty();
        assertThat(comment.getImageUrl()).isEqualTo(image);
        assertThat(created.image()).isEqualTo(image);
        ArgumentCaptor<com.luminous.api.notification.NotificationEvent> notification =
                ArgumentCaptor.forClass(com.luminous.api.notification.NotificationEvent.class);
        verify(eventPublisher).publishEvent(notification.capture());
        assertThat(notification.getValue().contentPreview()).isEqualTo("发送了一张图片");

        when(commentMapper.selectList(any())).thenReturn(List.of(comment));
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(sampleAuthor));
        assertThat(postService.comments(500L, null).getFirst().image()).isEqualTo(image);

        when(commentMapper.selectById(comment.getId())).thenReturn(comment);
        assertThat(postService.voteComment(comment.getId(), 1, commenter).image()).isEqualTo(image);
    }

    @Test
    void addComment_RejectsEmptyBodyAndInvalidImageBeforeInsert() {
        assertThatThrownBy(() -> postService.addComment(500L, new PostDtos.CreateCommentRequest("  "), currentUser))
                .isInstanceOf(BusinessException.class);
        String image = "https://example.com/not-uploaded.png";
        when(uploadService.requireOwnedImage(image, currentUser)).thenThrow(
                new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_COMMENT_IMAGE", "图片无效"));
        assertThatThrownBy(() -> postService.addComment(500L, new PostDtos.CreateCommentRequest("文字", image), currentUser))
                .isInstanceOf(BusinessException.class);
        verify(commentMapper, never()).insert(any(Comment.class));
    }

    @Test
    void commentRateLimitRejectsBeforeAnyCommentWrite() {
        doThrow(new CommentRateLimitException(5)).when(commentPublicationPolicy).check(currentUser.id());
        assertThatThrownBy(() -> postService.addComment(500L, new PostDtos.CreateCommentRequest("[E2E_TEST] 限流"), currentUser))
                .isInstanceOf(CommentRateLimitException.class);
        verify(commentMapper, never()).insert(any(Comment.class));
        verify(commentPublicationPolicy, never()).record(anyLong(), anyLong());
    }

    @Test
    void commentFloors_BoundsRowsCountsOnlyReturnedFloorsAndPreservesDeletedRoot() {
        LocalDateTime time = LocalDateTime.of(2026, 10, 13, 12, 0);
        when(commentMapper.selectFloors(500L, null, 3)).thenReturn(List.of(
                new CommentFloorRow(600L, 5, time, true), new CommentFloorRow(700L, 0, time, false),
                new CommentFloorRow(800L, 0, time, true)));
        Comment root = replyFixture(600L, 1L, null);
        when(commentMapper.selectBatchIds(List.of(600L))).thenReturn(List.of(root));
        when(commentMapper.countReplies(500L, List.of(600L, 700L))).thenReturn(List.of(
                new CommentReplyCount(600L, 4), new CommentReplyCount(700L, 2)));
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(sampleAuthor));
        var result = postService.commentFloors(500L, null, 2, null);
        assertThat(result.items()).hasSize(2);
        assertThat(result.items().getLast().root()).isNull();
        assertThat(result.items().getLast().replyCount()).isEqualTo(2);
        assertThat(result.hasNext()).isTrue();
        assertThat(CommentCursor.decode(result.nextCursor(), 500L, "floors").id()).isEqualTo(700L);
        verify(commentMapper, never()).selectList(any());
        verify(commentMapper, never()).selectReplies(any(), any(), any(), anyInt());
    }

    @Test
    void commentPages_RejectInvalidSizeAndCrossScopeCursorBeforeFetchingRows() {
        assertThatThrownBy(() -> postService.commentFloors(500L, null, 31, null)).isInstanceOf(BusinessException.class);
        String cursor = new CommentCursor(1, LocalDateTime.now(), 600L).encode(500L, "floors");
        assertThatThrownBy(() -> postService.commentReplies(500L, 600L, cursor, 5, null)).isInstanceOf(BusinessException.class);
        verify(commentMapper, never()).selectFloors(any(), any(), anyInt());
        verify(commentMapper, never()).selectReplies(any(), any(), any(), anyInt());
    }

    @Test
    void commentPages_HidePrivatePostAndRejectWrongFloor() {
        samplePost.setIsPublic(false);
        assertHidden(() -> postService.commentFloors(500L, null, 10, null));
        assertHidden(() -> postService.commentReplies(500L, 600L, null, 5, null));
        samplePost.setIsPublic(true);
        Comment child = replyFixture(700L, 1L, 600L);
        when(commentMapper.selectById(700L)).thenReturn(child);
        when(commentMapper.selectCount(any())).thenReturn(0L);
        assertThatThrownBy(() -> postService.commentReplies(500L, 700L, null, 5, null)).isInstanceOf(BusinessException.class);
    }

    @Test
    void commentReplies_UsesTimeCursorAndRejectsLegacyVoteCursor() {
        Comment root = replyFixture(600L, 1L, null);
        Comment first = replyFixture(700L, 1L, 600L);
        Comment second = replyFixture(800L, 1L, 600L);
        first.setVotes(20);
        when(commentMapper.selectById(600L)).thenReturn(root);
        when(commentMapper.selectCount(any())).thenReturn(2L);
        when(commentMapper.selectReplies(500L, 600L, null, 2)).thenReturn(List.of(first, second));
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(sampleAuthor));
        var page = postService.commentReplies(500L, 600L, null, 1, null);
        CommentCursor cursor = CommentCursor.decode(page.nextCursor(), 500L, CommentCursor.replyScope(600L));
        assertThat(cursor.votes()).isZero();
        assertThat(cursor.createdAt()).isEqualTo(first.getCreatedAt());
        assertThat(cursor.id()).isEqualTo(first.getId());
        String legacy = new CommentCursor(20, first.getCreatedAt(), first.getId()).encode(500L, "600");
        assertThatThrownBy(() -> postService.commentReplies(500L, 600L, legacy, 5, null))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getCode()).isEqualTo("INVALID_COMMENT_CURSOR"));
    }

    @Test
    void addComment_RejectsOversizeRootAndReplyBeforeAnyWrite() {
        assertThatThrownBy(() -> postService.addComment(500L,
                new PostDtos.CreateCommentRequest("字".repeat(301)), currentUser))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo("INVALID_COMMENT_LENGTH");
                    assertThat(ex.getMessage()).isEqualTo("评论最多 300 个字符");
                });
        assertThatThrownBy(() -> postService.addComment(500L,
                new PostDtos.CreateCommentRequest("字".repeat(151), null, 600L), currentUser))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo("INVALID_COMMENT_LENGTH");
                    assertThat(ex.getMessage()).isEqualTo("楼中楼回复最多 150 个字符");
                });
        verify(commentMapper, never()).insert(any(Comment.class));
        verify(postMapper, never()).updateCommentsCountAtomic(anyLong(), anyInt());
        verifyNoInteractions(eventPublisher, uploadService);
    }

    @Test
    void repliesAlwaysBelongToRootAndNotifyActualTarget() {
        Comment root = replyFixture(600L, 2L, null);
        Comment child = replyFixture(700L, 3L, 600L);
        when(commentMapper.selectById(600L)).thenReturn(root);
        when(commentMapper.selectById(700L)).thenReturn(child);
        when(userMapper.selectById(any(Long.class))).thenReturn(sampleAuthor);
        PostDtos.CommentDto direct = postService.addComment(500L,
                new PostDtos.CreateCommentRequest("[E2E_TEST] 回复主评论", null, 600L), currentUser);
        assertThat(direct.parentId()).isEqualTo("600");
        assertThat(direct.replyToCommentId()).isEqualTo("600");

        PostDtos.CommentDto nested = postService.addComment(500L,
                new PostDtos.CreateCommentRequest("[E2E_TEST] 回复楼内用户", null, 700L), currentUser);
        assertThat(nested.parentId()).isEqualTo("600");
        assertThat(nested.replyToCommentId()).isEqualTo("700");
        assertThat(nested.replyTo()).isNotNull();
        ArgumentCaptor<Comment> saved = ArgumentCaptor.forClass(Comment.class);
        verify(commentMapper, times(2)).insert(saved.capture());
        Comment deeperTarget = saved.getAllValues().getLast();
        when(commentMapper.selectById(deeperTarget.getId())).thenReturn(deeperTarget);
        PostDtos.CommentDto deeper = postService.addComment(500L,
                new PostDtos.CreateCommentRequest("[E2E_TEST] 继续楼内交流", null, deeperTarget.getId()), currentUser);
        assertThat(deeper.parentId()).isEqualTo("600");

        ArgumentCaptor<com.luminous.api.notification.NotificationEvent> notice =
                ArgumentCaptor.forClass(com.luminous.api.notification.NotificationEvent.class);
        verify(eventPublisher, times(2)).publishEvent(notice.capture());
        assertThat(notice.getAllValues()).extracting(com.luminous.api.notification.NotificationEvent::receiverId)
                .containsExactly(2L, 3L);
        assertThat(notice.getAllValues()).allMatch(event -> event.type().equals("REPLY_COMMENT"));

        when(commentMapper.selectList(any())).thenReturn(List.of(root, child));
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(sampleAuthor));
        PostDtos.CommentDto listed = postService.comments(500L, null).getLast();
        assertThat(listed.parentId()).isEqualTo("600");
        when(commentMapper.selectById(700L)).thenReturn(child);
        assertThat(postService.voteComment(700L, 1, currentUser).parentId()).isEqualTo("600");
    }

    @Test
    void replyRejectsMissingForeignTargetsAndAllImagesBeforeWrites() {
        assertThatThrownBy(() -> postService.addComment(500L,
                new PostDtos.CreateCommentRequest("[E2E_TEST] 无效对象", null, 999L), currentUser))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getCode()).isEqualTo("REPLY_TARGET_NOT_FOUND"));
        Comment foreign = replyFixture(700L, 2L, null); foreign.setPostId(501L);
        when(commentMapper.selectById(700L)).thenReturn(foreign);
        assertThatThrownBy(() -> postService.addComment(500L,
                new PostDtos.CreateCommentRequest("[E2E_TEST] 跨帖子对象", null, 700L), currentUser))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getCode()).isEqualTo("REPLY_TARGET_NOT_FOUND"));
        assertThatThrownBy(() -> postService.addComment(500L,
                new PostDtos.CreateCommentRequest("[E2E_TEST] 图片回复", "/api/files/image.png", 600L), currentUser))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getCode()).isEqualTo("REPLY_IMAGE_NOT_ALLOWED"));
        verify(commentMapper, never()).insert(any(Comment.class));
        verifyNoInteractions(uploadService);
    }

    @Test
    void deletingRootDoesNotCascadeOtherPeoplesReplies() {
        Comment root = replyFixture(600L, 1L, null);
        when(commentMapper.selectById(600L)).thenReturn(root);
        postService.deleteComment(600L, currentUser);
        verify(commentMapper).deleteById(600L);
        verify(commentMapper, never()).delete(any());
        verify(postMapper).updateCommentsCountAtomic(500L, -1);
    }

    private Comment replyFixture(long id, long authorId, Long parentId) {
        Comment comment = new Comment();
        comment.setId(id); comment.setPostId(500L); comment.setAuthorId(authorId);
        comment.setParentId(parentId); comment.setContent("[E2E_TEST] 楼内测试");
        comment.setVotes(0); comment.setCreatedAt(LocalDateTime.now());
        return comment;
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
