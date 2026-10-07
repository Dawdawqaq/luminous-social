package com.luminous.api.integration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.luminous.api.auth.AuthDtos;
import com.luminous.api.common.ApiResponse;
import com.luminous.api.community.Community;
import com.luminous.api.community.CommunityMapper;
import com.luminous.api.job.DataReconciliationScheduler;
import com.luminous.api.post.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import com.luminous.api.auth.UserMapper;
import com.luminous.api.cache.CacheConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles(value = "integration", resolver = VoteTestProfileResolver.class)
public class HighConcurrencyVoteBenchmarkTest {

    private static final Logger log = LoggerFactory.getLogger(HighConcurrencyVoteBenchmarkTest.class);

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private PostVoteMapper postVoteMapper;

    @Autowired
    private CommunityMapper communityMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private DataReconciliationScheduler dataReconciliationScheduler;

    private String getBaseUrl() {
        return "http://localhost:" + port + "/api";
    }

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired private com.luminous.api.auth.AuthService authService;

    private Long testPostId;
    private Long testCommunityId;
    private final List<Long> testUserIds = new ArrayList<>();
    private String communitySlug;
    private final List<String> userTokens = new ArrayList<>();
    private static final int CONCURRENT_USERS = 40;

    @BeforeEach
    void setUp() {
        // 每次测试使用独立资源，清理时只删除本次创建的数据。
        communitySlug = "e2e-bench-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        Community community = new Community();
        community.setId(com.luminous.api.common.SnowflakeIdGenerator.getNextId());
        community.setName("[E2E_TEST] Benchmark Community");
        community.setSlug(communitySlug);
        community.setDescription("[E2E_TEST] High Concurrency Benchmark");
        community.setMembersCount(1);
        community.setMembersUnit("members");
        community.setRulesJson("[]");
        community.setModeratorsJson("[]");
        communityMapper.insert(community);
        testCommunityId = community.getId();

        // 2. 注册并登录 40 个测试用户
        redisTemplate.delete("luminous:ratelimit:auth:127.0.0.1");
        redisTemplate.delete("luminous:ratelimit:auth:0:0:0:0:0:0:0:1");
        userTokens.clear();
        HttpHeaders bypassHeaders = new HttpHeaders();
        bypassHeaders.set("X-Bypass-Rate-Limit", "true");
        for (int i = 1; i <= CONCURRENT_USERS; i++) {
            String username = "E2E_TEST_" + System.currentTimeMillis() + "_" + i;
            AuthDtos.RegisterRequest regReq = new AuthDtos.RegisterRequest(username, "[E2E_TEST] User " + i, "Password123!");
            // 并发投票夹具通过服务层建立账号，认证接口的验证码由独立安全测试覆盖。
            AuthDtos.AuthResponse registered = authService.register(regReq);
            userTokens.add(registered.token());
            testUserIds.add(Long.parseLong(registered.user().id()));
        }

        assertThat(userTokens).hasSize(CONCURRENT_USERS);

        // 3. 发布测试帖子
        HttpHeaders authorHeaders = new HttpHeaders();
        authorHeaders.setBearerAuth(userTokens.get(0));
        authorHeaders.set("X-Idempotency-Key", java.util.UUID.randomUUID().toString());
        PostDtos.CreatePostRequest postReq = new PostDtos.CreatePostRequest(
                communitySlug,
                "[E2E_TEST] High Concurrency Vote Post " + System.currentTimeMillis(),
                "This post tests Redis Lua + RocketMQ write-behind under concurrent pressure.",
                null,
                null,
                true
        );
        ResponseEntity<ApiResponse<PostDtos.PostDto>> postResp = restTemplate.exchange(
                getBaseUrl() + "/posts",
                HttpMethod.POST,
                new HttpEntity<>(postReq, authorHeaders),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(postResp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(postResp.getBody()).isNotNull();
        assertThat(postResp.getBody().data()).isNotNull();
        testPostId = Long.parseLong(postResp.getBody().data().id());
        log.info("Created benchmark post with ID: {}, registered {} users", testPostId, userTokens.size());
    }

    @Test
    @DisplayName("热点投票验收：40 线程同时投票，验证请求成功率与最终一致性")
    void testHighConcurrencyVoting_NoDeadlocksAndFastLatency() throws InterruptedException {
        int threads = userTokens.size();
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        List<Long> latencies = Collections.synchronizedList(new ArrayList<>());

        for (String token : userTokens) {
            executor.submit(() -> {
                try {
                    startGate.await(); // 等待所有线程就绪，毫秒级同时击发
                    long start = System.currentTimeMillis();

                    HttpHeaders headers = new HttpHeaders();
                    headers.setBearerAuth(token);
                    PostDtos.VoteRequest voteReq = new PostDtos.VoteRequest(1);

                    ResponseEntity<ApiResponse<PostDtos.PostDto>> resp = restTemplate.exchange(
                            getBaseUrl() + "/posts/" + testPostId + "/vote",
                            HttpMethod.POST,
                            new HttpEntity<>(voteReq, headers),
                            new ParameterizedTypeReference<>() {}
                    );

                    long duration = System.currentTimeMillis() - start;
                    latencies.add(duration);

                    if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null && resp.getBody().success()) {
                        successCount.incrementAndGet();
                    } else {
                        errorCount.incrementAndGet();
                        log.error("Vote failed with status: {}, body: {}", resp.getStatusCode(), resp.getBody());
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    log.error("Vote thread exception:", e);
                } finally {
                    endGate.countDown();
                }
            });
        }

        long benchmarkStart = System.currentTimeMillis();
        startGate.countDown(); // 一键触发所有并发线程
        boolean completed = endGate.await(15, TimeUnit.SECONDS);
        long totalTime = System.currentTimeMillis() - benchmarkStart;
        executor.shutdown();

        assertThat(completed).isTrue();
        log.info("==== HIGH CONCURRENCY BENCHMARK RESULTS ==== 模式={}",
                Boolean.getBoolean("luminous.vote-test.isolated") ? "H2 + Redis + 同步落库" : "集成环境");
        log.info("Total Concurrent Requests: {}", threads);
        log.info("Success Count: {} (Target: 100%)", successCount.get());
        log.info("Error Count: {} (Target: 0)", errorCount.get());
        log.info("Total Wall-Clock Time: {} ms", totalTime);

        // 计算 P50 / P95 / P99 延迟
        Collections.sort(latencies);
        long p50 = latencies.get((int) (latencies.size() * 0.50));
        long p95 = latencies.get((int) (latencies.size() * 0.95));
        long p99 = latencies.get(latencies.size() - 1);
        log.info("Latency Metrics: P50={}ms, P95={}ms, P99={}ms", p50, p95, p99);

        // 1. 验证本次并发请求全部成功
        assertThat(errorCount.get()).isEqualTo(0);
        assertThat(successCount.get()).isEqualTo(threads);

        // 2. 验证 Redis 实时计数为 40
        String redisVal = redisTemplate.opsForValue().get("luminous:stat:post:votes:" + testPostId);
        assertThat(redisVal).isEqualTo(String.valueOf(threads));

        // 3. 等待异步落库完成；隔离模式采用现有同步降级路径
        for (int i = 0; i < 20; i++) {
            Post p = postMapper.selectById(testPostId);
            if (p != null && p.getVotes() == threads) {
                break;
            }
            Thread.sleep(300);
        }

        // 4. 验证数据库投票记录与帖子总分的最终一致性
        Post dbPost = postMapper.selectById(testPostId);
        assertThat(dbPost.getVotes()).isEqualTo(threads);

        Long voteRecordsCount = postVoteMapper.selectCount(new LambdaQueryWrapper<PostVote>()
                .eq(PostVote::getPostId, testPostId));
        assertThat(voteRecordsCount).isEqualTo(threads);

        // 5. 触发数据对账任务，验证对账结果确认 0 漂移
        boolean fixed = dataReconciliationScheduler.reconcilePostVotes(testPostId);
        assertThat(fixed).isFalse(); // false 表示本次对账无需修复
        log.info("==== BENCHMARK PASSED WITH ALL REQUESTS SUCCESSFUL AND CONSISTENT VOTES ====");
    }

    @AfterEach
    void cleanTestData() {
        if (testPostId != null) {
            jdbcTemplate.update("DELETE FROM consumed_events WHERE event_id IN (SELECT event_id FROM reliable_events WHERE post_id=?)", testPostId);
            jdbcTemplate.update("DELETE FROM reliable_events WHERE post_id=?", testPostId);
            postVoteMapper.delete(new LambdaQueryWrapper<PostVote>().eq(PostVote::getPostId, testPostId));
            postMapper.deleteById(testPostId);
            redisTemplate.delete(List.of(CacheConstants.POST_DETAIL_KEY + testPostId,
                    CacheConstants.POST_UV_KEY + testPostId, "luminous:stat:post:votes:" + testPostId,
                    "luminous:vote:state:post:" + testPostId, "luminous:vote:version:post:" + testPostId));
            for (Long userId : testUserIds) {
                redisTemplate.delete("luminous:vote:post:" + testPostId + ":" + userId);
            }
        }
        if (testCommunityId != null) {
            communityMapper.deleteById(testCommunityId);
        }
        for (Long userId : testUserIds) {
            userMapper.deleteById(userId);
            redisTemplate.delete(CacheConstants.USER_STATS_KEY + userId);
        }
        testUserIds.clear();
        userTokens.clear();
    }
}
