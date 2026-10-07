package com.luminous.api.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luminous.api.auth.*;
import com.luminous.api.cache.*;
import com.luminous.api.checkin.*;
import com.luminous.api.common.SnowflakeIdGenerator;
import com.luminous.api.feed.*;
import com.luminous.api.mq.*;
import com.luminous.api.notification.NotificationEvent;
import com.luminous.api.post.*;
import com.luminous.api.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 独立 Redis 与 H2 验证多节点缓存、提交时序、动态生命周期、UV 与原子签到。 */
class FeedCacheCheckInIntegrationTest extends AbstractVoteIntegrationTest {
    @Autowired FeedService feeds;
    @Autowired PostCacheService cache;
    @Autowired PostMapper posts;
    @Autowired RedissonClient redisson;
    @Autowired ObjectMapper mapper;
    @Autowired UserStatsCacheService stats;
    @Autowired CheckInService checkIns;
    @Autowired CheckInRepository checkInRepository;
    @Autowired CheckInHistoryImporter historyImporter;
    @Autowired ApplicationEventPublisher publisher;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired ReliableOutboxDispatcher dispatcher;
    @MockitoSpyBean NotificationMQProducer notifications;
    @MockitoSpyBean Clock clock;

    @Test void twoNodesSeeModificationAndDeletionWithoutLocalCopies() {
        Long id = createPost(true);
        PostCacheService second = new PostCacheService(redisTemplate, redisson, posts, mapper);
        assertThat(cache.queryWithPassThrough(id).getTitle()).contains("投票与权限");
        assertThat(second.queryWithPassThrough(id).getTitle()).contains("投票与权限");
        jdbcTemplate.update("UPDATE posts SET title=? WHERE id=?", "[E2E_TEST] 新标题", id);
        cache.invalidatePost(id);
        assertThat(second.queryWithPassThrough(id).getTitle()).isEqualTo("[E2E_TEST] 新标题");
        jdbcTemplate.update("DELETE FROM posts WHERE id=?", id);
        cache.invalidatePost(id);
        assertThat(second.queryWithPassThrough(id)).isNull();
    }

    @Test void failedInvalidationIsBoundedByFiveSecondExpiry() throws Exception {
        Long id = createPost(true);
        PostCacheService second = new PostCacheService(redisTemplate, redisson, posts, mapper);
        second.queryWithPassThrough(id);
        var broken = mock(org.springframework.data.redis.core.StringRedisTemplate.class);
        when(broken.delete(anyString())).thenThrow(new IllegalStateException("失效时 Redis 不可用"));
        PostCacheService first = new PostCacheService(broken, redisson, posts, mapper);
        jdbcTemplate.update("UPDATE posts SET title=? WHERE id=?", "[E2E_TEST] 过期后新标题", id);
        first.invalidatePost(id);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(6);
        Post result;
        do { result = second.queryWithPassThrough(id); if (result.getTitle().contains("过期后")) { break; } Thread.sleep(100); }
        while (System.nanoTime() < deadline);
        assertThat(result.getTitle()).contains("过期后");
    }

    @Test void rolledBackPostAndFollowProduceNoGhostFeedOrNotification() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        final long[] postId = new long[1];
        transaction.executeWithoutResult(status -> {
            authService.follow(author.username(), voter);
            PostDtos.PostDto post = postService.create(new PostDtos.CreatePostRequest(communitySlug,
                    "[E2E_TEST] 回滚帖子", "[E2E_TEST] 回滚内容", null, null, true), author);
            postId[0] = Long.parseLong(post.id());
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM reliable_events WHERE user_id=?", Long.class, author.id())).isEqualTo(1L);
            assertThat(redisTemplate.opsForZSet().zCard(CacheConstants.FEED_INBOX_KEY + voter.id())).isZero();
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notifications WHERE receiver_id=?", Long.class, author.id())).isZero();
            status.setRollbackOnly();
        });
        assertThat(posts.selectById(postId[0])).isNull();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_follows WHERE follower_id=?", Long.class, voter.id())).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM reliable_events WHERE user_id=?", Long.class, author.id())).isZero();
        assertThat(feeds.scrollFeed(voter, null, 0, 10).items()).isEmpty();
        assertThat(stats.getUserStats(author.id()).posts()).isZero();
    }

    @Test void committedNotificationIntentRecoversWhenImmediateDispatchIsInterrupted() {
        doNothing().when(notifications).dispatch(any());
        NotificationEvent event = new NotificationEvent(voter.id(), author.id(), "FOLLOW_USER", "USER", voter.id(), "[E2E_TEST] 提交后恢复");
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> publisher.publishEvent(event));
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM reliable_events WHERE event_id=?", String.class, event.eventId())).isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notifications WHERE receiver_id=?", Long.class, author.id())).isZero();
        doCallRealMethod().when(notifications).dispatch(any());
        dispatcher.retryPending();
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM reliable_events WHERE event_id=?", String.class, event.eventId())).isEqualTo("CONSUMED");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notifications WHERE receiver_id=?", Long.class, author.id())).isEqualTo(1L);
    }

    @Test void mixedAuthorsIncludingBigAuthorAreSortedDeduplicatedAndPaginatedAtSameMillisecond() {
        AuthDtos.AuthResponse bigAuth = register("big_author");
        CurrentUser big = new CurrentUser(Long.parseLong(bigAuth.user().id()), bigAuth.user().username(), "USER");
        authService.follow(author.username(), voter);
        authService.follow(big.username(), voter);
        LocalDateTime timestamp = LocalDateTime.now().minusMinutes(1).withNano(123000000);
        List<Long> expected = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            CurrentUser poster = i % 2 == 0 ? author : big;
            var result = postService.create(new PostDtos.CreatePostRequest(communitySlug,
                    "[E2E_TEST] 混合动态 " + i, "[E2E_TEST] 同毫秒分页", null, null, true), poster);
            long id = Long.parseLong(result.id()); postIds.add(id); expected.add(id);
            jdbcTemplate.update("UPDATE posts SET created_at=? WHERE id=?", timestamp, id);
        }
        UserFollowMapper bigFollowers = mock(UserFollowMapper.class);
        when(bigFollowers.selectCount(any())).thenReturn(10001L);
        new FeedService(redisTemplate, bigFollowers, posts, postService).fanOutToFollowers(big.id(), expected.get(1));
        verify(bigFollowers, never()).selectList(any());
        createPost(false);
        Long old = createPost(true);
        jdbcTemplate.update("UPDATE posts SET created_at=? WHERE id=?", timestamp.minusDays(31), old);
        // 收件箱重复、失效和缺失条目不能影响数据库统一拉取。
        redisTemplate.opsForZSet().add(CacheConstants.FEED_INBOX_KEY + voter.id(), String.valueOf(expected.getFirst()), 1);
        redisTemplate.opsForZSet().add(CacheConstants.FEED_INBOX_KEY + voter.id(), "999999999", 2);
        expected.sort(Comparator.reverseOrder());
        List<Long> actual = new ArrayList<>();
        Long score = null; int offset = 0;
        for (int page = 0; page < 4; page++) {
            var result = feeds.scrollFeed(voter, score, offset, 2);
            actual.addAll(result.items().stream().map(post -> Long.valueOf(post.id())).toList());
            score = result.minScore(); offset = result.offset();
            assertThat(offset).isEqualTo(Math.min((page + 1) * 2, 7));
            assertThat(result.hasMore()).isEqualTo(page < 3);
        }
        assertThat(actual).containsExactlyElementsOf(expected).doesNotHaveDuplicates();
    }

    @Test void newFollowIncludesHistoryAndRepeatedUnfollowDoesNotReduceCountsAgain() {
        Long history = createPost(true);
        assertThat(feeds.scrollFeed(voter, null, 0, 10).items()).isEmpty();
        authService.follow(author.username(), voter);
        assertThat(feeds.scrollFeed(voter, null, 0, 10).items()).extracting(PostDtos.PostDto::id).containsExactly(String.valueOf(history));
        assertThat(stats.getUserStats(author.id()).followers()).isEqualTo(1);
        assertThat(stats.getUserStats(voter.id()).following()).isEqualTo(1);
        redisTemplate.opsForZSet().add(CacheConstants.FEED_INBOX_KEY + voter.id(), String.valueOf(history), System.currentTimeMillis());
        authService.unfollow(author.username(), voter);
        assertThat(redisTemplate.opsForZSet().zCard(CacheConstants.FEED_INBOX_KEY + voter.id())).isZero();
        authService.unfollow(author.username(), voter);
        assertThat(stats.getUserStats(author.id()).followers()).isZero();
        assertThat(stats.getUserStats(voter.id()).following()).isZero();
        redisTemplate.opsForZSet().add(CacheConstants.FEED_INBOX_KEY + voter.id(), String.valueOf(history), System.currentTimeMillis());
        assertThat(feeds.scrollFeed(voter, null, 0, 10).items()).isEmpty();
    }

    @Test void inboxFanOutIsAtomicBoundedAndExpiring() {
        authService.follow(author.username(), voter);
        String key = CacheConstants.FEED_INBOX_KEY + voter.id();
        for (int i = 0; i < 1050; i++) { redisTemplate.opsForZSet().add(key, "e2e-old-" + i, i); }
        Long id = createPost(true);
        assertThat(redisTemplate.opsForZSet().zCard(key)).isEqualTo((long) FeedService.INBOX_CAPACITY);
        assertThat(redisTemplate.opsForZSet().score(key, String.valueOf(id))).isNotNull();
        assertThat(redisTemplate.getExpire(key)).isBetween(1L, FeedService.INBOX_TTL_SECONDS);
    }

    @Test void anonymousCookieKeepsUvStableAcrossRepeatedHttpRequests() {
        Long id = createPost(true);
        ResponseEntity<PostResponse> first = restTemplate.getForEntity("/api/posts/" + id, PostResponse.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        String cookie = first.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(cookie).contains("luminous_visitor=").contains("HttpOnly").contains("Max-Age=31536000");
        long initial = first.getBody().data().viewCount();
        HttpHeaders headers = new HttpHeaders(); headers.add(HttpHeaders.COOKIE, cookie.split(";")[0]);
        for (int i = 0; i < 5; i++) {
            var next = restTemplate.exchange("/api/posts/" + id, HttpMethod.GET, new HttpEntity<>(headers), PostResponse.class);
            assertThat(next.getBody().data().viewCount()).isEqualTo(initial);
        }
        String another = "luminous_visitor=" + UUID.randomUUID().toString().replace("-", "");
        headers.set(HttpHeaders.COOKIE, another);
        var separate = restTemplate.exchange("/api/posts/" + id, HttpMethod.GET, new HttpEntity<>(headers), PostResponse.class);
        assertThat(separate.getBody().data().viewCount()).isEqualTo(initial + 1);
    }
    public record PostResponse(boolean success, PostDtos.PostDto data) {}

    @Test void simultaneousCheckInSucceedsOnceAndHistorySurvivesRedisDeletionAcrossMonth() throws Exception {
        doReturn(Instant.parse("2026-09-30T16:00:00Z")).when(clock).instant();
        jdbcTemplate.update("INSERT INTO community_members(id,community_id,user_id) VALUES(?,?,?)", SnowflakeIdGenerator.getNextId(), communityId, voter.id());
        for (LocalDate date : List.of(LocalDate.of(2026, 9, 29), LocalDate.of(2026, 9, 30))) {
            checkInRepository.insert(communityId, voter.id(), date);
        }
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<CheckInDtos.CheckInResponse>> tasks = new ArrayList<>();
        try {
            for (int i = 0; i < 8; i++) { tasks.add(pool.submit(() -> { start.await(); return checkIns.checkIn(communitySlug, voter); })); }
            start.countDown(); int successes = 0;
            for (var task : tasks) {
                var result = task.get(10, TimeUnit.SECONDS); if (result.success()) { successes++; }
                assertThat(result.totalDays()).isEqualTo(1); assertThat(result.consecutiveDays()).isEqualTo(3);
            }
            assertThat(successes).isEqualTo(1);
        } finally { pool.shutdownNow(); }
        redisTemplate.delete(CacheConstants.CHECKIN_KEY + communitySlug + ":" + voter.id() + ":202610");
        assertThat(checkIns.getStats(communitySlug, voter).consecutiveDays()).isEqualTo(3);
        assertThat(checkIns.getStats(communitySlug, voter, "2026-09").checkedDays()).containsExactly(29, 30);
        assertThat(checkIns.getStats(communitySlug, voter, "2026-09").checkedToday()).isFalse();
        assertThat(checkIns.checkIn(communitySlug, voter).success()).isFalse();
    }

    @Test void legacyBitmapHistoryImportsOnceAndSurvivesExpiry() {
        doReturn(Instant.parse("2026-09-30T16:00:00Z")).when(clock).instant();
        String key = CacheConstants.CHECKIN_KEY + communitySlug + ":" + voter.id() + ":202609";
        redisTemplate.opsForValue().setBit(key, 28, true);
        redisTemplate.opsForValue().setBit(key, 29, true);
        assertThat(historyImporter.importMonth(key)).isEqualTo(2);
        assertThat(historyImporter.importMonth(key)).isZero();
        redisTemplate.delete(key);
        var result = checkIns.getStats(communitySlug, voter, "2026-09");
        assertThat(result.checkedDays()).containsExactly(29, 30);
        assertThat(result.consecutiveDays()).isEqualTo(2);
        assertThatThrownBy(() -> historyImporter.importMonth("other:project:key")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void historicalStatsAndFeedRejectInvalidHttpParameters() {
        assertStatus("/api/checkin/" + communitySlug + "/stats?month=2026-13", HttpMethod.GET, null, voterToken, HttpStatus.BAD_REQUEST);
        assertStatus("/api/checkin/" + communitySlug + "/stats", HttpMethod.GET, null, null, HttpStatus.UNAUTHORIZED);
        assertStatus("/api/feed?offset=-1", HttpMethod.GET, null, voterToken, HttpStatus.BAD_REQUEST);
        assertStatus("/api/feed?size=101", HttpMethod.GET, null, voterToken, HttpStatus.BAD_REQUEST);
    }

    @Test void rolledBackCheckInCreatesNeitherBitmapNorHistory() {
        doReturn(Instant.parse("2026-09-30T16:00:00Z")).when(clock).instant();
        jdbcTemplate.update("INSERT INTO community_members(id,community_id,user_id) VALUES(?,?,?)", SnowflakeIdGenerator.getNextId(), communityId, voter.id());
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertThat(checkIns.checkIn(communitySlug, voter).success()).isTrue(); status.setRollbackOnly();
        });
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM community_checkins WHERE community_id=? AND user_id=?", Long.class, communityId, voter.id())).isZero();
        assertThat(redisTemplate.hasKey(CacheConstants.CHECKIN_KEY + communitySlug + ":" + voter.id() + ":202610")).isFalse();
    }
}
