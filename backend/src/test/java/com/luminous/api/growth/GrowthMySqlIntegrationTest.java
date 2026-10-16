package com.luminous.api.growth;

import com.luminous.api.auth.AuthDtos;
import com.luminous.api.auth.AuthService;
import com.luminous.api.checkin.CheckInService;
import com.luminous.api.community.CommunityDtos;
import com.luminous.api.community.CommunityMapper;
import com.luminous.api.community.CommunityService;
import com.luminous.api.common.BusinessException;
import com.luminous.api.common.SnowflakeIdGenerator;
import com.luminous.api.post.PostDtos;
import com.luminous.api.post.PostService;
import com.luminous.api.post.CommentRateLimitException;
import com.luminous.api.post.CommentVoteMapper;
import com.luminous.api.security.CurrentUser;
import com.luminous.api.vote.VoteCommandService;
import org.mybatis.spring.SqlSessionTemplate;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 只连接固定 luminous-dev MySQL/Redis 端口，验证真实行锁与事务；每个批次均精确清理。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("growth-test")
@EnabledIfEnvironmentVariable(named = "RUN_GROWTH_MYSQL_TESTS", matches = "true")
class GrowthMySqlIntegrationTest {
    @Autowired AuthService auth;
    @Autowired CommunityService communities;
    @Autowired PostService posts;
    @Autowired CheckInService checkins;
    @MockitoSpyBean GrowthService growth;
    @MockitoSpyBean GrowthRepository repository;
    @Autowired GrowthProperties properties;
    @Autowired GrowthQueryService queries;
    @Autowired GrowthReadService reads;
    @Autowired VoteCommandService votes;
    @Autowired ApplicationEventPublisher events;
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired MutableClock clock;
    @Autowired MockMvc mvc;
    @Autowired SqlSessionTemplate mapperSession;
    @MockitoSpyBean CommunityMapper communityMapper;
    @MockitoSpyBean CommentVoteMapper commentVotes;

    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> communityIds = new ArrayList<>();
    private final List<String> slugs = new ArrayList<>();
    private CurrentUser author;
    private CurrentUser peer;
    private CurrentUser admin;
    private String authorToken;
    private String peerToken;
    private String adminToken;
    private String marker;
    private int sequence;

    @TestConfiguration
    static class ClockConfiguration {
        @Bean @Primary MutableClock growthTestClock() { return new MutableClock(); }
    }

    static class MutableClock extends Clock {
        private final AtomicReference<Instant> instant = new AtomicReference<>(Instant.parse("2026-10-11T15:59:40Z"));
        void reset() { instant.set(Instant.parse("2026-10-11T15:59:40Z")); }
        void advance(long seconds) { instant.updateAndGet(value -> value.plusSeconds(seconds)); }
        @Override public ZoneId getZone() { return ZoneId.of("Asia/Shanghai"); }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
        @Override public Instant instant() { return instant.get(); }
    }

    @BeforeEach
    void setUp() {
        clock.reset(); properties.setEnabled(true); properties.setReadEnabled(true);
        marker = System.getenv("GROWTH_TEST_MARKER");
        assertThat(marker).matches("\\[E2E_TEST] growth-[0-9]+");
        var authorAuth = register("作者"); author = current(authorAuth); authorToken = authorAuth.token();
        var peerAuth = register("读者"); peer = current(peerAuth); peerToken = peerAuth.token();
        var administrator = register("管理员");
        jdbc.update("UPDATE users SET role='ADMIN' WHERE id=?", Long.parseLong(administrator.user().id()));
        admin = new CurrentUser(Long.parseLong(administrator.user().id()), administrator.user().username(), "ADMIN");
        adminToken = auth.login(new AuthDtos.LoginRequest(admin.username(), "Password123!")).token();
        for (int index = 0; index < 2; index++) {
            String slug = "e2e-growth-" + UUID.randomUUID().toString().substring(0, 8);
            var community = communities.create(new CommunityDtos.CreateCommunityRequest(slug, marker + " 社区" + index,
                    "test", marker + " 成长事务验收"), author);
            communityIds.add(Long.parseLong(community.id())); slugs.add(slug);
            communities.join(slug, peer);
        }
    }

    private AuthDtos.AuthResponse register(String label) {
        String username = "e2e_g_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        var response = auth.register(new AuthDtos.RegisterRequest(username, marker + " " + label, "Password123!"));
        userIds.add(Long.parseLong(response.user().id()));
        return response;
    }
    private static CurrentUser current(AuthDtos.AuthResponse response) {
        return new CurrentUser(Long.parseLong(response.user().id()), response.user().username(), "USER");
    }
    private long createPost(int community) {
        return Long.parseLong(posts.create(new PostDtos.CreatePostRequest(slugs.get(community), "[E2E_TEST]帖" + (++sequence),
                marker + " 这是用于验证双维度成长的合规内容", null, null, true), author).id());
    }
    private long seedPost(int community) {
        long id = SnowflakeIdGenerator.getNextId();
        jdbc.update("INSERT INTO posts(id,author_id,community_id,title,content,is_public,status) VALUES(?,?,?,?,?,TRUE,'NORMAL')",
                id, author.id(), communityIds.get(community), marker + " 并发帖子" + (++sequence), marker + " 真实锁定读取验证内容");
        return id;
    }
    private long seedComment(long postId, String label) {
        long id = SnowflakeIdGenerator.getNextId();
        jdbc.update("INSERT INTO comments(id,post_id,author_id,content,votes) VALUES(?,?,?,?,0)",
                id, postId, peer.id(), marker + " " + label + " 独立有效评论" + id);
        jdbc.update("UPDATE posts SET comments_count=comments_count+1 WHERE id=?", postId);
        growth.created("COMMENT", id);
        return id;
    }
    private long experience(CurrentUser user, long scope) { return Objects.requireNonNull(repository.balance(user.id(), scope)); }

    @Test void realBusinessHooksMaintainSeparateScopesAndDuplicateCheckInDoesNotRewardTwice() {
        checkins.checkIn(slugs.get(0), author);
        checkins.checkIn(slugs.get(0), author);
        createPost(1);
        assertThat(experience(author, 0)).isEqualTo(20);
        assertThat(experience(author, communityIds.get(0))).isEqualTo(5);
        assertThat(experience(author, communityIds.get(1))).isEqualTo(15);
        assertThat(queries.overview(author.username(), slugs.get(1)).community().level()).isEqualTo(3);
    }

    @Test void joiningWhileMemberPostHoldsCommunityForeignKeyLockCommitsBothTransactions() throws Exception {
        // 发帖必须已加入社区；让另一名用户重新加入，保留真实外键锁等待场景。
        communities.leave(slugs.get(0), peer);
        long communityId = communityIds.get(0);
        CountDownLatch postInserted = new CountDownLatch(1);
        CountDownLatch joiningUpdatesCommunity = new CountDownLatch(1);
        // 真实插帖完成后保留社区外键共享锁，等加入事务走到人数更新再发放奖励。
        doAnswer(invocation -> {
            postInserted.countDown();
            if (!joiningUpdatesCommunity.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("等待加入社区更新人数超时");
            return invocation.callRealMethod();
        }).when(growth).created(eq("POST"), anyLong());
        doAnswer(invocation -> {
            joiningUpdatesCommunity.countDown();
            // MyBatis 接口方法为抽象方法，使用同一事务会话的原始代理执行真实 SQL。
            return mapperSession.getMapper(CommunityMapper.class).updateMembersCountAtomic(communityId, 1);
        }).when(communityMapper).updateMembersCountAtomic(eq(communityId), eq(1));
        var executor = Executors.newFixedThreadPool(2);
        try {
            var posting = executor.submit(() -> createPost(0));
            if (!postInserted.await(10, TimeUnit.SECONDS)) posting.get(1, TimeUnit.SECONDS);
            assertThat(postInserted.getCount()).isZero();
            var joining = executor.submit(() -> communities.join(slugs.get(0), peer));
            long postId = posting.get(20, TimeUnit.SECONDS);
            assertThat(joining.get(20, TimeUnit.SECONDS).joined()).isTrue();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM posts WHERE id=?", Long.class, postId)).isEqualTo(1);
            assertThat(experience(author, 0)).isEqualTo(15);
            assertThat(experience(author, communityId)).isEqualTo(15);
            assertThat(jdbc.queryForObject("SELECT members_count FROM communities WHERE id=?", Integer.class, communityId)).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM community_members WHERE community_id=?", Long.class, communityId)).isEqualTo(2);
        } finally {
            joiningUpdatesCommunity.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test void concurrentJoinPostAndCheckInMaintainMembershipAndGrowthBalances() throws Exception {
        for (int round = 0; round < 3; round++) {
            communities.leave(slugs.get(1), peer);
            // 推进专库中的发布门槛时间，保留真实限频规则和窗口内成功记录。
            jdbc.update("UPDATE post_publish_gates SET last_published_at=DATE_SUB(CURRENT_TIMESTAMP(6),INTERVAL 31 SECOND) WHERE user_id=?", author.id());
            CyclicBarrier start = new CyclicBarrier(3);
            int iteration = round;
            var executor = Executors.newFixedThreadPool(3);
            try {
                var joining = executor.submit(() -> { start.await(10, TimeUnit.SECONDS); return communities.join(slugs.get(1), peer); });
                var posting = executor.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    return posts.create(new PostDtos.CreatePostRequest(slugs.get(1), "[E2E_TEST]并发" + iteration,
                            marker + " 不同轮次有效发帖内容" + iteration, null, null, true), author);
                });
                var checkingIn = executor.submit(() -> { start.await(10, TimeUnit.SECONDS); return checkins.checkIn(slugs.get(0), author); });
                assertThat(joining.get(20, TimeUnit.SECONDS).joined()).isTrue();
                assertThat(posting.get(20, TimeUnit.SECONDS).id()).isNotBlank();
                assertThat(checkingIn.get(20, TimeUnit.SECONDS).totalDays()).isEqualTo(1);
            } finally { executor.shutdownNow(); assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue(); }
            assertThat(jdbc.queryForObject("SELECT members_count FROM communities WHERE id=?", Integer.class, communityIds.get(1))).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM community_members WHERE community_id=?", Long.class, communityIds.get(1))).isEqualTo(2);
        }
        assertThat(experience(author, 0)).isEqualTo(35);
        assertThat(experience(author, communityIds.get(0))).isEqualTo(5);
        assertThat(experience(author, communityIds.get(1))).isEqualTo(30);
        assertThat(jdbc.queryForObject("SELECT used_experience FROM growth_daily_usage WHERE user_id=? AND action='POST'", Long.class, author.id())).isEqualTo(30);
    }

    @Test void joiningRollbackRemovesMembershipCountAndNewGrowthAccountTogether() {
        communities.leave(slugs.get(1), author);
        long communityId = communityIds.get(1);
        // 此账户尚无奖励与流水，移除零余额以验证加入时的新建账户也参与回滚。
        jdbc.update("DELETE FROM growth_accounts WHERE user_id=? AND community_id=?", author.id(), communityId);
        var transaction = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> transaction.execute(status -> {
            assertThat(communities.join(slugs.get(1), author).joined()).isTrue();
            assertThat(repository.balance(author.id(), communityId)).isZero();
            throw new IllegalStateException("[E2E_TEST] 加入社区后业务失败");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(repository.balance(author.id(), communityId)).isNull();
        assertThat(experience(author, 0)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM community_members WHERE community_id=? AND user_id=?", Long.class,
                communityId, author.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT members_count FROM communities WHERE id=?", Integer.class, communityId)).isEqualTo(1);
    }

    @Test void publishedStatusRemainsEligibleAndVisibleWithoutInventingHistoricalExperience() {
        long id = seedPost(0);
        jdbc.update("UPDATE posts SET status='PUBLISHED' WHERE id=?", id);
        assertThat(repository.lockPost(id).eligible()).isTrue();
        assertThat(experience(author, 0)).isZero();
        assertThat(posts.list("new", slugs.get(0), null, author.username(), 1, 20, null).items())
                .extracting(PostDtos.PostDto::id).contains(String.valueOf(id));
        growth.created("POST", id);
        assertThat(experience(author, 0)).isEqualTo(15);
    }

    @Test void parallelDuplicatesAndCrossCommunityContributionsShareGlobalDailyQuota() throws Exception {
        long first = seedPost(0);
        concurrently(Collections.nCopies(6, first), id -> growth.created("POST", id));
        assertThat(experience(author, 0)).isEqualTo(15);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM growth_rewards WHERE user_id=?", Long.class, author.id())).isEqualTo(1);
        List<Long> ids = new ArrayList<>();
        for (int index = 0; index < 6; index++) ids.add(seedPost(index % 2));
        concurrently(ids, id -> growth.created("POST", id));
        assertThat(experience(author, 0)).isEqualTo(30);
        assertThat(experience(author, communityIds.get(0)) + experience(author, communityIds.get(1))).isEqualTo(30);
        assertThat(jdbc.queryForObject("SELECT used_experience FROM growth_daily_usage WHERE user_id=? AND action='POST'", Long.class, author.id())).isEqualTo(30);
        clock.advance(30);
        for (Long id : ids) growth.created("POST", id);
        assertThat(experience(author, 0)).isEqualTo(30);
    }

    @Test void businessRollbackRemovesRewardLedgerQuotaAndContentTogether() {
        var transaction = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> transaction.execute(status -> {
            createPost(0);
            throw new IllegalStateException("[E2E_TEST] 模拟业务后续写入失败");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(experience(author, 0)).isZero();
        assertThat(experience(author, communityIds.get(0))).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM posts WHERE author_id=?", Long.class, author.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM growth_rewards WHERE user_id=?", Long.class, author.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM growth_daily_usage WHERE user_id=?", Long.class, author.id())).isZero();
    }

    @Test void duplicateVoteCancellationSwitchAndRestoreNeverRefillQuota() {
        long id = createPost(0);
        votes.accept(id, peer.id(), 1);
        votes.accept(id, peer.id(), 1);
        assertThat(experience(author, 0)).isEqualTo(17);
        votes.accept(id, peer.id(), -1);
        assertThat(experience(author, 0)).isEqualTo(15);
        votes.accept(id, peer.id(), 0);
        clock.advance(30);
        votes.accept(id, peer.id(), 1);
        votes.accept(id, author.id(), 1);
        growth.liked("POST", id, peer.id());
        assertThat(experience(author, 0)).isEqualTo(17);
        assertThat(jdbc.queryForObject("SELECT SUM(used_experience) FROM growth_daily_usage WHERE user_id=? AND action='LIKE'", Long.class, author.id())).isEqualTo(2);
        votes.accept(id, peer.id(), 0);
        growth.liked("POST", id, peer.id());
        assertThat(experience(author, 0)).isEqualTo(15);
    }

    @Test void concurrentCheckInAndIncomingLikesDoNotInvertUserAndBalanceLocks() throws Exception {
        long id = createPost(0);
        List<Long> readers = new ArrayList<>();
        for (int index = 0; index < 6; index++) readers.add(current(register("并发点赞者" + index)).id());
        concurrently(readers, reader -> {
            if (reader % 2 == 0) checkins.checkIn(slugs.get(0), author);
            votes.accept(id, reader, 1);
            checkins.checkIn(slugs.get(0), author);
        });
        assertThat(experience(author, 0)).isEqualTo(30);
        assertThat(experience(author, communityIds.get(0))).isEqualTo(30);
    }

    @Test void cappedLikesCannotBeReclaimedOnAnotherDay() {
        long id = createPost(0);
        List<CurrentUser> readers = new ArrayList<>();
        for (int index = 0; index < 6; index++) readers.add(current(register("限额读者" + index)));
        for (var reader : readers) votes.accept(id, reader.id(), 1);
        assertThat(experience(author, 0)).isEqualTo(25);
        clock.advance(30);
        votes.accept(id, readers.getLast().id(), 0);
        votes.accept(id, readers.getLast().id(), 1);
        assertThat(experience(author, 0)).isEqualTo(25);
    }

    @Test void moderationDowngradesOnceAndRetainsOtherUsersCompliantCommentRewards() {
        long id = createPost(0);
        long comment = Long.parseLong(posts.addComment(id, new PostDtos.CreateCommentRequest(marker + " 合规讨论评论"), peer).id());
        votes.accept(id, peer.id(), 1);
        assertThat(experience(author, 0)).isEqualTo(17);
        assertThat(experience(peer, 0)).isEqualTo(3);
        growth.banPost(id, "[E2E_TEST] 违规封禁");
        growth.banPost(id, "[E2E_TEST] 重复封禁");
        assertThat(queries.progress(author.id(), 0).level()).isEqualTo(1);
        assertThat(experience(author, 0)).isZero();
        assertThat(experience(author, communityIds.get(0))).isZero();
        assertThat(experience(peer, 0)).isEqualTo(3);
        posts.deletePost(id, admin);
        growth.removed("POST", id, true, "[E2E_TEST] 重复删除事件");
        assertThat(experience(author, 0)).isZero();
        assertThat(experience(peer, 0)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comments WHERE id=?", Long.class, comment)).isZero();
    }

    @Test void normalPostDeletionPreservesExperienceAndDeletedPostCanBeProsecutedThroughAdminApi() throws Exception {
        long id = createPost(0);
        votes.accept(id, peer.id(), 1);
        posts.addComment(id, new PostDtos.CreateCommentRequest(marker + " 合规评论应保留经验"), peer);
        posts.deletePost(id, author);
        assertThat(experience(author, 0)).isEqualTo(17);
        assertThat(experience(author, communityIds.get(0))).isEqualTo(17);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM growth_rewards WHERE content_type='POST' AND content_id=? AND state='ACTIVE'", Long.class, id)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT used_experience FROM growth_daily_usage WHERE user_id=? AND action='POST'", Long.class, author.id())).isEqualTo(15);
        String path = "/api/admin/posts/" + id + "/ban";
        mvc.perform(post(path).header("Authorization", "Bearer " + peerToken).contentType("application/json")
                .content("{\"reason\":\"[E2E_TEST] 非管理员不能追责\"}")).andExpect(status().isForbidden());
        mvc.perform(post(path).header("Authorization", "Bearer " + adminToken).contentType("application/json")
                .content("{\"reason\":\"[E2E_TEST] 删除后确认帖子违规\"}")).andExpect(status().isOk());
        assertThat(experience(author, 0)).isZero();
        assertThat(experience(author, communityIds.get(0))).isZero();
        assertThat(queries.progress(author.id(), 0).level()).isEqualTo(1);
        assertThat(experience(peer, 0)).isEqualTo(3);
        mvc.perform(post(path).header("Authorization", "Bearer " + adminToken).contentType("application/json")
                .content("{\"reason\":\"[E2E_TEST] 重复追责\"}")).andExpect(status().isOk());
        growth.removed("POST", id, true, "[E2E_TEST] 删除事件重放");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM growth_ledger l JOIN growth_rewards r ON r.id=l.reward_id WHERE r.content_type='POST' AND r.content_id=? AND l.operation='REVOKE'", Long.class, id)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM posts WHERE id=?", Long.class, id)).isZero();
        assertThat(jdbc.queryForObject("SELECT used_experience FROM growth_daily_usage WHERE user_id=? AND action='POST'", Long.class, author.id())).isEqualTo(15);
    }

    @Test void normallyDeletedCommentCanBeProsecutedAfterParentDeletionWithRewardsDisabled() throws Exception {
        long id = createPost(0);
        long comment = Long.parseLong(posts.addComment(id, new PostDtos.CreateCommentRequest(marker + " 作者后续自行删除评论"), peer).id());
        posts.voteComment(comment, 1, author);
        posts.deleteComment(comment, peer);
        assertThat(experience(peer, 0)).isEqualTo(5);
        posts.deletePost(id, author);
        properties.setEnabled(false);
        String path = "/api/admin/comments/" + comment + "/revoke-growth";
        mvc.perform(post(path).header("Authorization", "Bearer " + peerToken).contentType("application/json")
                .content("{\"reason\":\"[E2E_TEST] 无权撤销\"}")).andExpect(status().isForbidden());
        mvc.perform(post(path).header("Authorization", "Bearer " + adminToken).contentType("application/json")
                .content("{\"reason\":\"[E2E_TEST] 后续确认评论违规\"}")).andExpect(status().isOk());
        assertThat(experience(peer, 0)).isZero();
        assertThat(experience(peer, communityIds.get(0))).isZero();
        assertThat(experience(author, 0)).isEqualTo(15);
        mvc.perform(post(path).header("Authorization", "Bearer " + adminToken).contentType("application/json")
                .content("{\"reason\":\"[E2E_TEST] 重复处理\"}")).andExpect(status().isOk());
        growth.removed("COMMENT", comment, true, "[E2E_TEST] 评论删除事件重放");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM growth_ledger l JOIN growth_rewards r ON r.id=l.reward_id WHERE r.content_type='COMMENT' AND r.content_id=? AND l.operation='REVOKE'", Long.class, comment)).isEqualTo(2);
    }

    @Test void concurrentModerationOfDeletedSourceRevokesExactlyOnce() throws Exception {
        long id = createPost(0);
        posts.deletePost(id, author);
        concurrently(Collections.nCopies(6, id), postId -> growth.banPost(postId, "[E2E_TEST] 并发违规追责"));
        assertThat(experience(author, 0)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM growth_ledger l JOIN growth_rewards r ON r.id=l.reward_id WHERE r.content_type='POST' AND r.content_id=? AND l.operation='REVOKE'", Long.class, id)).isEqualTo(1);
    }

    @Test void moderationRacingWithAuthorDeletionCannotLeaveActiveReward() throws Exception {
        long id = createPost(0);
        var transaction = new TransactionTemplate(transactionManager);
        CountDownLatch deleted = new CountDownLatch(1);
        CountDownLatch moderationStarted = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var deleting = executor.submit(() -> transaction.execute(status -> {
                posts.deletePost(id, author);
                deleted.countDown();
                try { if (!moderationStarted.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("等待追责开始超时"); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
                return null;
            }));
            assertThat(deleted.await(10, TimeUnit.SECONDS)).isTrue();
            var moderating = executor.submit(() -> {
                moderationStarted.countDown();
                growth.banPost(id, "[E2E_TEST] 删除事务期间发起追责");
            });
            deleting.get(15, TimeUnit.SECONDS); moderating.get(15, TimeUnit.SECONDS);
            assertThat(experience(author, 0)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM growth_rewards WHERE content_type='POST' AND content_id=? AND state='ACTIVE'", Long.class, id)).isZero();
        } finally { moderationStarted.countDown(); executor.shutdownNow(); assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue(); }
    }

    @Test void administratorDeletingOwnContentNormallyDoesNotRevokeExperience() {
        long id = createPost(0);
        var administratorAuthor = new CurrentUser(author.id(), author.username(), "ADMIN");
        posts.deletePost(id, administratorAuthor);
        assertThat(experience(author, 0)).isEqualTo(15);
        assertThat(experience(author, communityIds.get(0))).isEqualTo(15);
    }

    @Test void unknownModerationSourceStillReturnsNotFoundAndInvalidReasonDoesNotRevoke() throws Exception {
        long id = createPost(0);
        posts.deletePost(id, author);
        mvc.perform(post("/api/admin/posts/" + id + "/ban").header("Authorization", "Bearer " + adminToken)
                .contentType("application/json").content("{\"reason\":\"\"}")).andExpect(status().isBadRequest());
        assertThat(experience(author, 0)).isEqualTo(15);
        for (String path : List.of("/api/admin/posts/0/ban", "/api/admin/comments/0/revoke-growth")) {
            mvc.perform(post(path).header("Authorization", "Bearer " + adminToken).contentType("application/json")
                    .content("{\"reason\":\"[E2E_TEST] 不存在来源\"}")).andExpect(status().isNotFound());
        }
    }

    @Test void moderatedCommentRevokesBaseAndRecognitionWithoutAffectingPostOwner() {
        long id = createPost(0);
        long comment = Long.parseLong(posts.addComment(id, new PostDtos.CreateCommentRequest(marker + " 评论自己的独立观点"), peer).id());
        posts.voteComment(comment, 1, author);
        posts.voteComment(comment, 1, author);
        assertThat(experience(peer, 0)).isEqualTo(5);
        posts.voteComment(comment, 0, author);
        posts.voteComment(comment, 1, author);
        posts.deleteComment(comment, admin);
        growth.removed("COMMENT", comment, true, "[E2E_TEST] 重复删除");
        assertThat(experience(peer, 0)).isZero();
        assertThat(experience(author, 0)).isEqualTo(15);
    }

    @Test void concurrentFirstVotesOnDifferentPostCommentsCommitAndKeepGrowthConsistent() throws Exception {
        long firstComment = seedComment(seedPost(0), "跨帖首次投票甲");
        long secondComment = seedComment(seedPost(1), "跨帖首次投票乙");
        CyclicBarrier bothRead = new CyclicBarrier(2);
        doAnswer(invocation -> {
            // 执行真实当前读后同步两个事务，修复前读不到行，修复后应读到各自初始化的零状态。
            var vote = mapperSession.getMapper(CommentVoteMapper.class).selectForUpdate(invocation.getArgument(0), invocation.getArgument(1));
            bothRead.await(10, TimeUnit.SECONDS);
            return vote;
        }).when(commentVotes).selectForUpdate(anyLong(), eq(author.id()));
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> posts.voteComment(firstComment, 1, author));
            var second = executor.submit(() -> posts.voteComment(secondComment, 1, author));
            assertThat(first.get(20, TimeUnit.SECONDS).votes()).isEqualTo(1);
            assertThat(second.get(20, TimeUnit.SECONDS).votes()).isEqualTo(1);
        } finally { executor.shutdownNow(); assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue(); }
        for (long id : new long[]{firstComment, secondComment}) {
            assertThat(jdbc.queryForObject("SELECT votes FROM comments WHERE id=?", Integer.class, id)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comment_votes WHERE comment_id=? AND value=1", Long.class, id)).isEqualTo(1);
        }
        assertThat(experience(peer, 0)).isEqualTo(10);
        assertThat(experience(peer, communityIds.get(0))).isEqualTo(5);
        assertThat(experience(peer, communityIds.get(1))).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT used_experience FROM growth_daily_usage WHERE user_id=? AND action='LIKE'", Long.class, peer.id())).isEqualTo(4);
    }

    @Test void concurrentFirstCommentLikesForDifferentAuthorsCommitWithConsistentRewards() throws Exception {
        long first = seedComment(seedPost(0), "不同作者首次点赞甲");
        long secondPost = seedPost(1);
        long second = SnowflakeIdGenerator.getNextId();
        jdbc.update("INSERT INTO comments(id,post_id,author_id,content,votes) VALUES(?,?,?,?,0)",
                second, secondPost, admin.id(), marker + " 不同作者首次点赞乙");
        jdbc.update("UPDATE posts SET comments_count=comments_count+1 WHERE id=?", secondPost);
        growth.created("COMMENT", second);
        String firstSource = "LIKE:COMMENT:" + first + ":" + author.id();
        String secondSource = "LIKE:COMMENT:" + second + ":" + author.id();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM growth_rewards WHERE source_key IN (?,?)",
                Long.class, firstSource, secondSource)).isZero();
        CyclicBarrier bothRead = new CyclicBarrier(2);
        Set<String> visited = ConcurrentHashMap.newKeySet();
        doAnswer(invocation -> {
            String source = invocation.getArgument(0);
            var reward = invocation.callRealMethod();
            // 执行真实来源当前读再同步；修复前缺行，修复后读取本事务建立的来源。
            if (Set.of(firstSource, secondSource).contains(source) && visited.add(source)) {
                bothRead.await(10, TimeUnit.SECONDS);
            }
            return reward;
        }).when(repository).reward(org.mockito.ArgumentMatchers.anyString());
        var executor = Executors.newFixedThreadPool(2);
        try {
            var firstVote = executor.submit(() -> posts.voteComment(first, 1, author));
            var secondVote = executor.submit(() -> posts.voteComment(second, 1, author));
            assertThat(firstVote.get(20, TimeUnit.SECONDS).votes()).isEqualTo(1);
            assertThat(secondVote.get(20, TimeUnit.SECONDS).votes()).isEqualTo(1);
        } finally { executor.shutdownNow(); assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue(); }
        assertThat(visited).containsExactlyInAnyOrder(firstSource, secondSource);
        for (long id : new long[]{first, second}) {
            assertThat(jdbc.queryForObject("SELECT votes FROM comments WHERE id=?", Long.class, id)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT SUM(value) FROM comment_votes WHERE comment_id=?", Long.class, id)).isEqualTo(1);
            String source = "LIKE:COMMENT:" + id + ":" + author.id();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM growth_rewards WHERE source_key=? AND state='ACTIVE' AND amount=2 AND revision=1",
                    Long.class, source)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM growth_ledger l JOIN growth_rewards r ON r.id=l.reward_id WHERE r.source_key=? AND l.operation='GRANT' AND l.delta=2 AND l.revision=1",
                    Long.class, source)).isEqualTo(1);
            posts.voteComment(id, 1, author);
        }
        assertThat(experience(peer, 0)).isEqualTo(5);
        assertThat(experience(peer, communityIds.get(0))).isEqualTo(5);
        assertThat(experience(peer, communityIds.get(1))).isZero();
        assertThat(experience(admin, 0)).isEqualTo(5);
        assertThat(experience(admin, communityIds.get(1))).isEqualTo(5);
        for (long owner : new long[]{peer.id(), admin.id()}) {
            assertThat(jdbc.queryForObject("SELECT SUM(used_experience) FROM growth_daily_usage WHERE user_id=? AND action='LIKE'", Long.class, owner)).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT SUM(l.delta) FROM growth_ledger l JOIN growth_rewards r ON r.id=l.reward_id WHERE r.user_id=?", Long.class, owner)).isEqualTo(5);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM growth_rewards WHERE user_id IN (?,?) AND state='PENDING'",
                Long.class, peer.id(), admin.id())).isZero();
    }

    @Test void commentVoteStateMatrixKeepsRowsScoreRewardAndDailyQuotaConsistent() {
        long id = seedComment(seedPost(0), "评论投票状态矩阵");
        for (int value : new int[]{0, 1, 1, 0, 0, 1, -1, 1, 0}) {
            assertThat(posts.voteComment(id, value, author).votes()).isEqualTo(value);
            assertThat(jdbc.queryForObject("SELECT votes FROM comments WHERE id=?", Integer.class, id)).isEqualTo(value);
            assertThat(jdbc.queryForObject("SELECT COALESCE(SUM(value),0) FROM comment_votes WHERE comment_id=?", Integer.class, id)).isEqualTo(value);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comment_votes WHERE comment_id=?", Long.class, id)).isEqualTo(value == 0 ? 0 : 1);
            assertThat(experience(peer, 0)).isEqualTo(value == 1 ? 5 : 3);
            assertThat(experience(peer, communityIds.get(0))).isEqualTo(value == 1 ? 5 : 3);
        }
        assertThat(jdbc.queryForObject("SELECT used_experience FROM growth_daily_usage WHERE user_id=? AND action='LIKE'", Long.class, peer.id())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT state FROM growth_rewards WHERE source_key=?", String.class, "LIKE:COMMENT:" + id + ":" + author.id())).isEqualTo("CANCELLED");
    }

    @Test void rewardSourceInitializationRequiresExistingBusinessTransaction() {
        String source = "CHECK_IN:" + author.id() + ":" + communityIds.get(0) + ":" + LocalDate.now(clock);
        var content = new GrowthRepository.Content("COMMUNITY", communityIds.get(0), author.id(), communityIds.get(0), 0, true, "");
        assertThatThrownBy(() -> repository.initializeReward(source, content, null, "CHECK_IN", LocalDate.now(clock), properties.getRuleVersion()))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM growth_rewards WHERE source_key=?", Long.class, source)).isZero();
    }

    @Test void rewardDecisionFailureRollsBackNewSourceAndLegalCommentVoteTogether() {
        long id = seedComment(seedPost(0), "来源决策失败回滚");
        doAnswer(invocation -> { throw new IllegalStateException("[E2E_TEST] 奖励决策失败"); })
                .when(repository).reserve(eq(peer.id()), org.mockito.ArgumentMatchers.any(LocalDate.class), eq("LIKE"), anyLong(), anyLong());
        assertThatThrownBy(() -> posts.voteComment(id, 1, author)).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM growth_rewards WHERE source_key=?", Long.class,
                "LIKE:COMMENT:" + id + ":" + author.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comment_votes WHERE comment_id=?", Long.class, id)).isZero();
        assertThat(jdbc.queryForObject("SELECT votes FROM comments WHERE id=?", Long.class, id)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM growth_daily_usage WHERE user_id=? AND action='LIKE'", Long.class, peer.id())).isZero();
        assertThat(experience(peer, 0)).isEqualTo(3);
        assertThat(experience(peer, communityIds.get(0))).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM growth_ledger l JOIN growth_rewards r ON r.id=l.reward_id WHERE r.user_id=?", Long.class, peer.id())).isEqualTo(1);
    }

    @Test void oldSnapshotCannotReawardSourceCommittedAfterSnapshotWasCreated() throws Exception {
        long id = seedPost(0);
        String source = "POST:" + id;
        CountDownLatch snapshotReady = new CountDownLatch(1);
        CountDownLatch awarded = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var replay = executor.submit(() -> repeatableReadTransaction().execute(status -> {
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM growth_rewards WHERE source_key=?", Long.class, source)).isZero();
                snapshotReady.countDown(); awaitGrowthGate(awarded);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM growth_rewards WHERE source_key=?", Long.class, source)).isZero();
                return growth.created("POST", id);
            }));
            assertThat(snapshotReady.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(growth.created("POST", id)).isEqualTo(15);
            awarded.countDown();
            assertThat(replay.get(20, TimeUnit.SECONDS)).isZero();
        } finally { awarded.countDown(); executor.shutdownNow(); assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue(); }
        assertThat(experience(author, 0)).isEqualTo(15);
        assertThat(experience(author, communityIds.get(0))).isEqualTo(15);
        assertThat(jdbc.queryForObject("SELECT used_experience FROM growth_daily_usage WHERE user_id=? AND action='POST'", Long.class, author.id())).isEqualTo(15);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM growth_ledger l JOIN growth_rewards r ON r.id=l.reward_id WHERE r.source_key=?", Long.class, source)).isEqualTo(1);
    }

    @Test void oldSnapshotCancelsLatestCommittedCommentVoteWithoutWrongScoreDelta() throws Exception {
        long id = seedComment(seedPost(0), "旧快照评论投票");
        CountDownLatch snapshotReady = new CountDownLatch(1);
        CountDownLatch voteCommitted = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var cancelling = executor.submit(() -> repeatableReadTransaction().execute(status -> {
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comment_votes WHERE comment_id=?", Long.class, id)).isZero();
                snapshotReady.countDown(); awaitGrowthGate(voteCommitted);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comment_votes WHERE comment_id=?", Long.class, id)).isZero();
                return posts.voteComment(id, 0, author);
            }));
            assertThat(snapshotReady.await(10, TimeUnit.SECONDS)).isTrue();
            posts.voteComment(id, 1, author);
            assertThat(experience(peer, 0)).isEqualTo(5);
            voteCommitted.countDown();
            assertThat(cancelling.get(20, TimeUnit.SECONDS).votes()).isZero();
        } finally { voteCommitted.countDown(); executor.shutdownNow(); assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue(); }
        assertThat(jdbc.queryForObject("SELECT votes FROM comments WHERE id=?", Integer.class, id)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comment_votes WHERE comment_id=?", Long.class, id)).isZero();
        assertThat(experience(peer, 0)).isEqualTo(3);
        assertThat(experience(peer, communityIds.get(0))).isEqualTo(3);
    }

    @Test void realCommentCooldownRejectsImmediateRepeatEvenWhenGrowthClockAdvances() {
        long id = createPost(0);
        posts.addComment(id, new PostDtos.CreateCommentRequest(marker + " 冷却前正常发布"), peer);
        clock.advance(30);
        assertThatThrownBy(() -> posts.addComment(id, new PostDtos.CreateCommentRequest(marker + " 冷却内重复发布"), peer))
                .isInstanceOfSatisfying(CommentRateLimitException.class, error -> {
                    assertThat(error.getStatus().value()).isEqualTo(429);
                    assertThat(error.getRetryAfterSeconds()).isBetween(1, 5);
                });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comments WHERE post_id=?", Long.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comment_publications WHERE user_id=?", Long.class, peer.id())).isEqualTo(1);
        assertThat(experience(peer, 0)).isEqualTo(3);
        assertThat(experience(peer, communityIds.get(0))).isEqualTo(3);
    }

    @Test void duplicateContentSelfCommentsAndPerPostLimitDoNotProduceExtraBaseRewards() {
        long id = createPost(0);
        posts.addComment(id, new PostDtos.CreateCommentRequest(marker + " 作者自身回复"), author);
        for (int index = 0; index < 4; index++) {
            awaitCommentCooldown(peer.id());
            posts.addComment(id, new PostDtos.CreateCommentRequest(marker + " 同帖独立观点" + index), peer);
        }
        assertThat(experience(author, 0)).isEqualTo(15);
        assertThat(experience(peer, 0)).isEqualTo(6);
        clock.advance(30);
        awaitCommentCooldown(peer.id());
        posts.addComment(id, new PostDtos.CreateCommentRequest(marker + " 同帖独立观点0"), peer);
        assertThat(experience(peer, 0)).isEqualTo(6);
    }

    private void awaitCommentCooldown(long userId) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (true) {
            // 发布间隔按数据库时间计算，成长测试时钟不能替代真实五秒冷却。
            long remaining = jdbc.queryForList("SELECT GREATEST(0,COALESCE(TIMESTAMPDIFF(MICROSECOND,CURRENT_TIMESTAMP(6),"
                            + "last_published_at + INTERVAL 5 SECOND),0)) FROM comment_publish_gates WHERE user_id=?",
                    Long.class, userId).stream().findFirst().orElse(0L);
            if (remaining == 0) return;
            if (System.nanoTime() >= deadline) throw new IllegalStateException("等待真实评论冷却超时");
            try { Thread.sleep(Math.min(5000, (remaining + 999) / 1000 + 5)); }
            catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
        }
    }

    @Test void timedReadSessionsRequireOwnerDurationAndUniqueReadersWhileDetailViewsDoNotReward() {
        long id = createPost(0);
        posts.detail(id, peer);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM growth_qualified_reads WHERE post_id=?", Long.class, id)).isZero();
        var session = reads.begin(id, peer);
        assertThatThrownBy(() -> reads.complete(id, session.token(), peer)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> reads.complete(id, session.token(), author)).isInstanceOf(BusinessException.class);
        clock.advance(10);
        assertThat(reads.complete(id, session.token(), peer).firstQualifiedRead()).isTrue();
        assertThat(reads.complete(id, session.token(), peer).firstQualifiedRead()).isFalse();
        var again = reads.begin(id, peer);
        clock.advance(10);
        assertThat(reads.complete(id, again.token(), peer).firstQualifiedRead()).isFalse();
        var reader = current(register("第二个有效读者"));
        var second = reads.begin(id, reader);
        clock.advance(10);
        assertThat(reads.complete(id, second.token(), reader).experienceAwarded()).isEqualTo(1);
        assertThat(experience(author, 0)).isEqualTo(16);
        assertThat(experience(author, communityIds.get(0))).isEqualTo(16);
        var expired = reads.begin(id, peer);
        clock.advance(61);
        assertThatThrownBy(() -> reads.complete(id, expired.token(), peer)).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getCode()).isEqualTo("READ_SESSION_EXPIRED"));
    }

    @Test void disabledRewardsStillPermitRequiredReversalAndMissingDataDoesNotInventLevels() {
        long id = createPost(0);
        properties.setEnabled(false);
        assertThat(auth.getUserProfile(author.username(), null).growth()).isNull();
        growth.banPost(id, "[E2E_TEST] 奖励关闭期间仍撤销违规内容");
        assertThat(experience(author, 0)).isZero();
        properties.setEnabled(true);
        jdbc.update("DELETE FROM growth_accounts WHERE user_id=? AND community_id=?", peer.id(), communityIds.get(0));
        assertThat(queries.progress(peer.id(), communityIds.get(0))).isNull();
    }

    @Test void realApiReturnsProgressLedgerAndEnforcesModerationAndLedgerPermissions() throws Exception {
        long id = createPost(0);
        mvc.perform(get("/api/users/" + author.username())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.growth.experience").value(15)).andExpect(jsonPath("$.data.growth.level").value(3));
        mvc.perform(get("/api/users/" + author.username() + "/growth").param("community", slugs.get(0)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.community.level").value(3))
                .andExpect(jsonPath("$.data.personal.nextLevelExperience").value(30));
        mvc.perform(get("/api/posts/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.author.personalGrowth.level").value(3))
                .andExpect(jsonPath("$.data.author.communityGrowth.level").value(3));
        mvc.perform(get("/api/growth/ledger")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/growth/ledger").header("Authorization", "Bearer " + authorToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].delta").value(15));
        mvc.perform(post("/api/admin/posts/" + id + "/ban").header("Authorization", "Bearer " + peerToken)
                .contentType("application/json").content("{\"reason\":\"[E2E_TEST] 无权封禁\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/posts/" + id + "/ban").header("Authorization", "Bearer " + adminToken)
                .contentType("application/json").content("{\"reason\":\"[E2E_TEST] 违规封禁\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/posts/" + id)).andExpect(status().isNotFound());
    }

    @Test void oldRepeatableReadSnapshotCannotBypassQuotaAfterWaitingOnUserBalance() throws Exception {
        long first = seedPost(0); long second = seedPost(1); long third = seedPost(0);
        CountDownLatch ready = new CountDownLatch(1); CountDownLatch committed = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var future = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                assertThat(experience(author, 0)).isZero();
                ready.countDown();
                try { if (!committed.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("等待提交超时"); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
                growth.created("POST", third);
                return null;
            }));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            growth.created("POST", first); growth.created("POST", second);
            committed.countDown(); future.get(15, TimeUnit.SECONDS);
            assertThat(experience(author, 0)).isEqualTo(30);
        } finally { committed.countDown(); executor.shutdownNow(); assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue(); }
    }

    @ParameterizedTest
    @ValueSource(strings = {"作者首次", "作者恢复", "点赞者首次", "点赞者恢复"})
    void oldSnapshotCannotGrantOrRestoreLikeAfterParticipantBan(String scenario) throws Exception {
        long id = createPost(0);
        boolean restoring = scenario.endsWith("恢复");
        long bannedUserId = scenario.startsWith("作者") ? author.id() : peer.id();
        if (restoring) { votes.accept(id, peer.id(), 1); votes.accept(id, peer.id(), 0); }
        assertThat(experience(author, 0)).isEqualTo(15);
        withOldSnapshotAfterBan(bannedUserId, () -> votes.accept(id, peer.id(), 1));
        assertThat(experience(author, 0)).isEqualTo(15);
        assertThat(experience(author, communityIds.get(0))).isEqualTo(15);
        String source = "LIKE:POST:" + id + ":" + peer.id();
        if (restoring) {
            assertThat(jdbc.queryForObject("SELECT state FROM growth_rewards WHERE source_key=?", String.class, source)).isEqualTo("CANCELLED");
        } else {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM growth_rewards WHERE source_key=?", Long.class, source)).isZero();
        }
        assertThat(jdbc.queryForObject("SELECT COALESCE(SUM(used_experience),0) FROM growth_daily_usage WHERE user_id=? AND action='LIKE'", Long.class,
                author.id())).isEqualTo(restoring ? 2 : 0);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM growth_ledger l JOIN growth_rewards r ON r.id=l.reward_id WHERE r.source_key=? AND l.operation='RESTORE'", Long.class, source)).isZero();
    }

    @Test void oldSnapshotCannotRewardNewPostAfterAuthorBan() throws Exception {
        withOldSnapshotAfterBan(author.id(), () -> createPost(0));
        assertThat(experience(author, 0)).isZero();
        assertThat(experience(author, communityIds.get(0))).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM growth_rewards WHERE user_id=? AND action='POST' AND state='SUPPRESSED' AND amount=0", Long.class,
                author.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM growth_daily_usage WHERE user_id=?", Long.class, author.id())).isZero();
    }

    @Test void oldSnapshotCannotQualifyReaderAfterReaderBan() throws Exception {
        long id = createPost(0);
        var session = reads.begin(id, peer);
        clock.advance(11);
        withOldSnapshotAfterBan(peer.id(), () -> assertThatThrownBy(() -> reads.complete(id, session.token(), peer))
                .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getStatus().value()).isEqualTo(403)), true);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM growth_qualified_reads WHERE post_id=?", Long.class, id)).isZero();
        assertThat(jdbc.queryForObject("SELECT completed FROM growth_read_sessions WHERE token=?", Boolean.class, session.token())).isFalse();
        assertThat(experience(author, 0)).isEqualTo(15);
    }

    @Test void qualificationCurrentReadWaitsForBanTransactionAndReadsCommittedState() throws Exception {
        CountDownLatch snapshotReady = new CountDownLatch(1);
        CountDownLatch banWritten = new CountDownLatch(1);
        CountDownLatch probeStarted = new CountDownLatch(1);
        CountDownLatch commitBan = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var reading = executor.submit(() -> repeatableReadTransaction().execute(status -> {
                assertThat(jdbc.queryForObject("SELECT is_banned FROM users WHERE id=?", Boolean.class, author.id())).isFalse();
                snapshotReady.countDown(); awaitGrowthGate(banWritten);
                probeStarted.countDown();
                return repository.activeUser(author.id());
            }));
            assertThat(snapshotReady.await(10, TimeUnit.SECONDS)).isTrue();
            var banning = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                jdbc.update("UPDATE users SET is_banned=TRUE WHERE id=?", author.id());
                banWritten.countDown(); awaitGrowthGate(commitBan);
                return null;
            }));
            assertThat(probeStarted.await(10, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> reading.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            commitBan.countDown(); banning.get(15, TimeUnit.SECONDS);
            assertThat(reading.get(15, TimeUnit.SECONDS)).isFalse();
        } finally {
            banWritten.countDown(); commitBan.countDown();
            executor.shutdownNow(); assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test void mutualLikesAndBothAuthorsCheckInDoNotInvertParticipantAndAccountLocks() throws Exception {
        long authorPost = createPost(0);
        long peerPost = Long.parseLong(posts.create(new PostDtos.CreatePostRequest(slugs.get(1), "[E2E_TEST]读者帖",
                marker + " 用于双向点赞资格锁与签到并发验证", null, null, true), peer).id());
        // 先建立投票时钟、取消来源和签到记录，避免首次插入的间隙锁干扰本次资格锁验证。
        votes.accept(authorPost, peer.id(), 1); votes.accept(authorPost, peer.id(), 0);
        votes.accept(peerPost, author.id(), 1); votes.accept(peerPost, author.id(), 0);
        checkins.checkIn(slugs.get(0), author); checkins.checkIn(slugs.get(0), peer);
        List<Runnable> operations = List.of(() -> votes.accept(authorPost, peer.id(), 1),
                () -> votes.accept(peerPost, author.id(), 1), () -> checkins.checkIn(slugs.get(0), author),
                () -> checkins.checkIn(slugs.get(0), peer));
        CyclicBarrier start = new CyclicBarrier(operations.size());
        var executor = Executors.newFixedThreadPool(operations.size());
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (Runnable operation : operations) futures.add(executor.submit(() -> { start.await(10, TimeUnit.SECONDS); operation.run(); return null; }));
            for (Future<?> future : futures) future.get(20, TimeUnit.SECONDS);
        } finally { executor.shutdownNow(); assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue(); }
        assertThat(experience(author, 0)).isEqualTo(22);
        assertThat(experience(peer, 0)).isEqualTo(22);
        assertThat(experience(author, communityIds.get(0))).isEqualTo(22);
        assertThat(experience(peer, communityIds.get(0))).isEqualTo(5);
        assertThat(experience(peer, communityIds.get(1))).isEqualTo(17);
    }

    private void withOldSnapshotAfterBan(long userId, Runnable action) throws Exception {
        withOldSnapshotAfterBan(userId, action, false);
    }

    private void withOldSnapshotAfterBan(long userId, Runnable action, boolean rollbackExpected) throws Exception {
        CountDownLatch snapshotReady = new CountDownLatch(1);
        CountDownLatch banCommitted = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var future = executor.submit(() -> repeatableReadTransaction().execute(status -> {
                // 用普通查询建立旧快照，不能用待修复的资格方法，否则其共享锁会阻止封禁提交。
                assertThat(jdbc.queryForObject("SELECT is_banned FROM users WHERE id=?", Boolean.class, userId)).isFalse();
                assertThat(jdbc.queryForObject("SELECT @@transaction_isolation", String.class)).isEqualTo("REPEATABLE-READ");
                snapshotReady.countDown(); awaitGrowthGate(banCommitted);
                assertThat(jdbc.queryForObject("SELECT is_banned FROM users WHERE id=?", Boolean.class, userId)).isFalse();
                action.run();
                // 预期的业务拒绝已将内层事务标为回滚，外层也明确回滚，不能将其误当提交失败。
                if (rollbackExpected) status.setRollbackOnly();
                return null;
            }));
            assertThat(snapshotReady.await(10, TimeUnit.SECONDS)).isTrue();
            jdbc.update("UPDATE users SET is_banned=TRUE WHERE id=?", userId);
            assertThat(jdbc.queryForObject("SELECT is_banned FROM users WHERE id=?", Boolean.class, userId)).isTrue();
            banCommitted.countDown(); future.get(20, TimeUnit.SECONDS);
        } finally { banCommitted.countDown(); executor.shutdownNow(); assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue(); }
    }

    private TransactionTemplate repeatableReadTransaction() {
        var transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return transaction;
    }

    private static void awaitGrowthGate(CountDownLatch gate) {
        try { if (!gate.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("等待成长并发测试栅栏超时"); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
    }

    private void concurrently(List<Long> ids, java.util.function.LongConsumer operation) throws Exception {
        var executor = Executors.newFixedThreadPool(6);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (Long id : ids) futures.add(executor.submit(() -> operation.accept(id)));
            for (Future<?> future : futures) future.get(20, TimeUnit.SECONDS);
        } finally { executor.shutdownNow(); assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue(); }
    }

    /** 用户标识契约复用独立开发数据库、鉴权链和精确清理，覆盖全部受影响接口。 */
    @Nested
    class UserIdentifierContractTests {
        private CurrentUser registerNumericUsername(String username) throws Exception {
            var registration = new AuthDtos.RegisterRequest(username, marker + " 数字用户名", "Password123!");
            // 校验真实注册 DTO 后复用注册业务；本组验证用户接口，不绕过注册验证码接口。
            try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
                assertThat(factory.getValidator().validate(registration)).isEmpty();
            }
            long id = Long.parseLong(auth.register(registration).user().id());
            userIds.add(id);
            return new CurrentUser(id, username, "USER");
        }

        private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(
                String operation, String identifier, String type) {
            return request(operation, identifier, type, UUID.randomUUID().toString());
        }

        private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(
                String operation, String identifier, String type, String key) {
            String path = "/api/users/" + identifier;
            var request = switch (operation) {
                case "follow" -> post(path + "/follow");
                case "unfollow" -> delete(path + "/follow");
                case "followers" -> get(path + "/followers");
                case "following" -> get(path + "/following");
                default -> get(path);
            };
            request.header("Authorization", "Bearer " + peerToken).header("X-Bypass-Rate-Limit", "true")
                    .header("X-Idempotency-Key", key);
            if (type != null) request.queryParam("identifierType", type);
            return request;
        }

        private void assertAllEndpointsFail(String identifier, String type, int code, String error) throws Exception {
            for (String operation : List.of("profile", "follow", "unfollow", "followers", "following")) {
                mvc.perform(request(operation, identifier, type)).andExpect(status().is(code))
                        .andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.error.code").value(error));
            }
        }

        private void awaitFollowCooldown() throws InterruptedException {
            // 等待数据库真实一秒间隔，不修改限流时钟、记录或生产开关。
            for (int attempt = 0; attempt < 10; attempt++) {
                long micros = jdbc.queryForObject("SELECT GREATEST(0,TIMESTAMPDIFF(MICROSECOND,CURRENT_TIMESTAMP(6),"
                        + "last_changed_at + INTERVAL 1 SECOND)) FROM follow_action_gates WHERE user_id=?", Long.class, peer.id());
                if (micros == 0) return;
                Thread.sleep(Math.min(1100, (micros + 999) / 1000 + 10));
            }
            throw new IllegalStateException("[E2E_TEST] 等待关注冷却超时");
        }

        @Test void ambiguousLegacyIdentifierRejectsAllEndpointsWithoutChangingExistingFollow() throws Exception {
            CurrentUser numeric = registerNumericUsername(String.valueOf(author.id()));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id=? OR username=?", Long.class,
                    author.id(), numeric.username())).isEqualTo(2);
            auth.follow(author.username(), peer);
            String gate = jdbc.queryForObject("SELECT CAST(last_changed_at AS CHAR) FROM follow_action_gates WHERE user_id=?", String.class, peer.id());
            long notifications = jdbc.queryForObject("SELECT COUNT(*) FROM reliable_events WHERE user_id=?", Long.class, peer.id());
            assertAllEndpointsFail(numeric.username(), null, 409, "USER_IDENTIFIER_AMBIGUOUS");
            assertThat(jdbc.queryForObject("SELECT following_id FROM user_follows WHERE follower_id=?", Long.class, peer.id())).isEqualTo(author.id());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM follow_actions WHERE user_id=?", Long.class, peer.id())).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM follow_notification_windows WHERE actor_id=?", Long.class, peer.id())).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT CAST(last_changed_at AS CHAR) FROM follow_action_gates WHERE user_id=?", String.class, peer.id())).isEqualTo(gate);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reliable_events WHERE user_id=?", Long.class, peer.id())).isEqualTo(notifications);
            assertThat(auth.getUserProfile(author.username(), peer).stats().followers()).isEqualTo(1);
            assertThat(auth.getUserProfile(numeric.username(), peer, com.luminous.api.auth.UserIdentifierType.USERNAME).stats().followers()).isZero();
        }

        @ParameterizedTest @ValueSource(strings = {"username", "id"})
        void explicitTypeSelectsCorrectTargetForAllFiveEndpoints(String type) throws Exception {
            CurrentUser numeric = registerNumericUsername(String.valueOf(author.id()));
            long target = type.equals("username") ? numeric.id() : author.id();
            long other = type.equals("username") ? author.id() : numeric.id();
            String identifier = numeric.username();
            mvc.perform(request("profile", identifier, type.toUpperCase(Locale.ROOT))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(String.valueOf(target)));
            mvc.perform(request("followers", identifier, type)).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
            mvc.perform(request("following", identifier, type)).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
            mvc.perform(request("follow", identifier, type)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(String.valueOf(target))).andExpect(jsonPath("$.data.isFollowing").value(true));
            mvc.perform(request("unfollow", identifier, type)).andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.error.code").value("FOLLOW_RATE_LIMITED"));
            mvc.perform(request("followers", identifier, type)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items[0].id").value(String.valueOf(peer.id()))).andExpect(jsonPath("$.data.total").value(1));
            mvc.perform(request("following", String.valueOf(peer.id()), "id")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items[0].id").value(String.valueOf(target)));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_follows WHERE follower_id=? AND following_id=?", Long.class, peer.id(), other)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM follow_notification_windows WHERE actor_id=? AND receiver_id=?", Long.class, peer.id(), target)).isEqualTo(1);
            awaitFollowCooldown();
            mvc.perform(request("unfollow", identifier, type)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(String.valueOf(target))).andExpect(jsonPath("$.data.isFollowing").value(false));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_follows WHERE follower_id=?", Long.class, peer.id())).isZero();
            assertThat(auth.currentUser(peer).stats().following()).isZero();
            assertThat(auth.getUserProfile(identifier, peer, com.luminous.api.auth.UserIdentifierType.parse(type)).stats().followers()).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM follow_actions WHERE user_id=?", Long.class, peer.id())).isEqualTo(2);
        }

        @Test void legacyLookupPreservesUnambiguousNamesIdsAndLongNumericUsername() throws Exception {
            for (String identifier : List.of(author.username(), String.valueOf(author.id()))) {
                mvc.perform(request("profile", identifier, null)).andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.id").value(String.valueOf(author.id())));
                mvc.perform(request("followers", identifier, null)).andExpect(status().isOk());
                mvc.perform(request("following", identifier, null)).andExpect(status().isOk());
                mvc.perform(request("follow", identifier, null)).andExpect(status().isOk()).andExpect(jsonPath("$.data.isFollowing").value(true));
                awaitFollowCooldown();
                mvc.perform(request("unfollow", identifier, null)).andExpect(status().isOk()).andExpect(jsonPath("$.data.isFollowing").value(false));
                awaitFollowCooldown();
            }
            String digits = "9".repeat(32 - String.valueOf(author.id()).length()) + author.id();
            CurrentUser numeric = registerNumericUsername(digits);
            mvc.perform(request("profile", digits, null)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(String.valueOf(numeric.id())));
            mvc.perform(request("follow", digits, "username")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(String.valueOf(numeric.id())));
        }

        @Test void invalidTypeInvalidIdAndMissingNamespaceDoNotFallBackOrConsumeQuota() throws Exception {
            CurrentUser numeric = registerNumericUsername(String.valueOf(author.id()));
            assertAllEndpointsFail(numeric.username(), "unknown", 400, "INVALID_USER_IDENTIFIER_TYPE");
            for (String invalid : List.of("abc", "0", "-1", "9223372036854775808")) {
                assertAllEndpointsFail(invalid, "id", 400, "INVALID_USER_ID");
            }
            assertAllEndpointsFail(String.valueOf(numeric.id()), "username", 404, "USER_NOT_FOUND");
            assertAllEndpointsFail(String.valueOf(Long.MAX_VALUE), "id", 404, "USER_NOT_FOUND");
            assertAllEndpointsFail("missing_" + UUID.randomUUID(), null, 404, "USER_NOT_FOUND");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM follow_actions WHERE user_id=?", Long.class, peer.id())).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM follow_action_gates WHERE user_id=?", Long.class, peer.id())).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_follows WHERE follower_id=?", Long.class, peer.id())).isZero();
        }

        @Test void sameUserMatchingIdAndUsernameIsNotAmbiguous() throws Exception {
            CurrentUser numeric = registerNumericUsername(String.valueOf(author.id()));
            jdbc.update("UPDATE users SET username=? WHERE id=?", String.valueOf(numeric.id()), numeric.id());
            mvc.perform(request("profile", String.valueOf(numeric.id()), null)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(String.valueOf(numeric.id())));
        }

        @Test void idempotencyKeyCannotReplayFollowForAnotherIdentifierNamespace() throws Exception {
            CurrentUser numeric = registerNumericUsername(String.valueOf(author.id()));
            String key = UUID.randomUUID().toString();
            for (int attempt = 0; attempt < 2; attempt++) {
                var result = mvc.perform(request("follow", numeric.username(), "username", key))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(String.valueOf(numeric.id())));
                if (attempt == 1) result.andExpect(header().string("Idempotency-Replayed", "true"));
            }
            mvc.perform(request("follow", numeric.username(), "id", key))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_MISMATCH"));
            assertThat(jdbc.queryForObject("SELECT following_id FROM user_follows WHERE follower_id=?", Long.class, peer.id())).isEqualTo(numeric.id());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM follow_actions WHERE user_id=?", Long.class, peer.id())).isEqualTo(1);
        }
    }

    @AfterEach
    void cleanup() {
        properties.setEnabled(true); properties.setReadEnabled(true);
        try {
            for (Long userId : userIds) {
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM growth_rewards WHERE user_id=? AND state='PENDING'", Long.class, userId)).isZero();
                Long active = jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM growth_rewards WHERE user_id=? AND state='ACTIVE'", Long.class, userId);
                Long ledger = jdbc.queryForObject("SELECT COALESCE(SUM(l.delta),0) FROM growth_ledger l JOIN growth_rewards r ON r.id=l.reward_id WHERE r.user_id=?", Long.class, userId);
                assertThat(repository.balance(userId, 0)).isEqualTo(active);
                assertThat(ledger).isEqualTo(active);
                Long scoped = jdbc.queryForObject("SELECT COALESCE(SUM(experience),0) FROM growth_accounts WHERE user_id=? AND community_id<>0", Long.class, userId);
                assertThat(scoped).isEqualTo(active);
            }
        } finally {
            // 并行通知模块的去重回执不依赖用户外键，必须在删除本例用户前按归属清理。
            boolean hasLikeReceipts = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables "
                    + "WHERE table_schema=DATABASE() AND table_name='notification_like_receipts'", Long.class) != 0;
            Set<Long> postIds = new HashSet<>();
            for (Long userId : userIds) {
                // 删除数据库记录前保存缓存归属，已删除内容仍能从成长来源中追溯。
                postIds.addAll(jdbc.queryForList("SELECT id FROM posts WHERE author_id=? UNION SELECT content_id FROM growth_rewards WHERE user_id=? AND content_type='POST'", Long.class, userId, userId));
            }
            for (Long userId : userIds) {
                if (hasLikeReceipts) jdbc.update("DELETE FROM notification_like_receipts WHERE receiver_id=? OR actor_id=?", userId, userId);
                jdbc.update("DELETE FROM notifications WHERE receiver_id=? OR actor_id=?", userId, userId);
                jdbc.update("DELETE FROM consumed_events WHERE event_id IN (SELECT event_id FROM reliable_events WHERE user_id=? OR post_id IN (SELECT id FROM posts WHERE author_id=?))", userId, userId);
                jdbc.update("DELETE FROM failed_messages WHERE event_id IN (SELECT event_id FROM reliable_events WHERE user_id=? OR post_id IN (SELECT id FROM posts WHERE author_id=?))", userId, userId);
                jdbc.update("DELETE FROM reliable_events WHERE user_id=? OR post_id IN (SELECT id FROM posts WHERE author_id=?)", userId, userId);
                jdbc.update("DELETE FROM comments WHERE author_id=?", userId);
                jdbc.update("DELETE FROM posts WHERE author_id=?", userId);
                jdbc.update("DELETE FROM users WHERE id=?", userId);
                scanDelete("luminous:*" + userId + "*");
            }
            for (Long communityId : communityIds) jdbc.update("DELETE FROM communities WHERE id=?", communityId);
            for (String slug : slugs) scanDelete("luminous:*" + slug + "*");
            for (Long postId : postIds) scanDelete("luminous:*" + postId + "*");
            userIds.clear(); communityIds.clear(); slugs.clear();
        }
    }
    private void scanDelete(String pattern) {
        try (var cursor = redis.scan(ScanOptions.scanOptions().match(pattern).count(100).build())) {
            while (cursor.hasNext()) redis.delete(cursor.next());
        }
    }
}
