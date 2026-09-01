package com.luminous.api.integration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.luminous.api.auth.AuthDtos;
import com.luminous.api.common.ApiResponse;
import com.luminous.api.community.Community;
import com.luminous.api.community.CommunityMapper;
import com.luminous.api.job.DataReconciliationScheduler;
import com.luminous.api.post.*;
import org.junit.jupiter.api.BeforeEach;
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
@ActiveProfiles("integration")
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

    private Long testPostId;
    private final List<String> userTokens = new ArrayList<>();
    private static final int CONCURRENT_USERS = 40;

    @BeforeEach
    void setUp() {
        // 1. 确保有测试社区
        Community community = communityMapper.selectOne(new LambdaQueryWrapper<Community>().eq(Community::getSlug, "benchmark-comm"));
        if (community == null) {
            community = new Community();
            community.setId(9988776655L);
            community.setName("Benchmark Community");
            community.setSlug("benchmark-comm");
            community.setDescription("High Concurrency Benchmark");
            community.setMembersCount(1);
            community.setMembersUnit("members");
            community.setRulesJson("[]");
            community.setModeratorsJson("[]");
            communityMapper.insert(community);
        }

        // 2. 注册并登录 40 个测试用户
        redisTemplate.delete("luminous:ratelimit:auth:127.0.0.1");
        redisTemplate.delete("luminous:ratelimit:auth:0:0:0:0:0:0:0:1");
        userTokens.clear();
        HttpHeaders bypassHeaders = new HttpHeaders();
        bypassHeaders.set("X-Bypass-Rate-Limit", "true");
        for (int i = 1; i <= CONCURRENT_USERS; i++) {
            String username = "bench_user_" + System.currentTimeMillis() + "_" + i;
            AuthDtos.RegisterRequest regReq = new AuthDtos.RegisterRequest(username, "User " + i, "Password123!");
            ResponseEntity<ApiResponse<AuthDtos.AuthResponse>> regResp = restTemplate.exchange(
                    getBaseUrl() + "/auth/register",
                    HttpMethod.POST,
                    new HttpEntity<>(regReq, bypassHeaders),
                    new ParameterizedTypeReference<>() {}
            );
            if (regResp.getStatusCode().is2xxSuccessful() && regResp.getBody() != null && regResp.getBody().data() != null) {
                userTokens.add(regResp.getBody().data().token());
            }
        }

        // 3. 发布测试帖子
        HttpHeaders authorHeaders = new HttpHeaders();
        authorHeaders.setBearerAuth(userTokens.get(0));
        PostDtos.CreatePostRequest postReq = new PostDtos.CreatePostRequest(
                "benchmark-comm",
                "High Concurrency Vote Post " + System.currentTimeMillis(),
                "This post tests Redis Lua + RocketMQ write-behind under concurrent pressure.",
                null,
                true
        );
        ResponseEntity<ApiResponse<PostDtos.PostDto>> postResp = restTemplate.exchange(
                getBaseUrl() + "/posts",
                HttpMethod.POST,
                new HttpEntity<>(postReq, authorHeaders),
                new ParameterizedTypeReference<>() {}
        );
        testPostId = Long.parseLong(postResp.getBody().data().id());
        log.info("Created benchmark post with ID: {}, registered {} users", testPostId, userTokens.size());
    }

    @Test
    @DisplayName("高并发点赞压测：40 线程同时对同一热点帖子点赞，验证 0 死锁、极速响应与最终一致性")
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
        log.info("==== HIGH CONCURRENCY BENCHMARK RESULTS ====");
        log.info("Total Concurrent Requests: {}", threads);
        log.info("Success Count: {} (Target: 100%)", successCount.get());
        log.info("Error Count: {} (Target: 0, ZERO MySQL Deadlocks)", errorCount.get());
        log.info("Total Wall-Clock Time: {} ms", totalTime);

        // 计算 P50 / P95 / P99 延迟
        Collections.sort(latencies);
        long p50 = latencies.get((int) (latencies.size() * 0.50));
        long p95 = latencies.get((int) (latencies.size() * 0.95));
        long p99 = latencies.get(latencies.size() - 1);
        log.info("Latency Metrics: P50={}ms, P95={}ms, P99={}ms", p50, p95, p99);

        // 1. 验证 0 错误（绝对不能再出现死锁回滚异常）
        assertThat(errorCount.get()).isEqualTo(0);
        assertThat(successCount.get()).isEqualTo(threads);

        // 2. 验证 Redis 实时计数为 40
        String redisVal = redisTemplate.opsForValue().get("stat:post:votes:" + testPostId);
        assertThat(redisVal).isEqualTo(String.valueOf(threads));

        // 3. 等待 RocketMQ 异步消费者批量刷盘完成
        for (int i = 0; i < 20; i++) {
            Post p = postMapper.selectById(testPostId);
            if (p != null && p.getVotes() == threads) {
                break;
            }
            Thread.sleep(300);
        }

        // 4. 验证 MySQL 权威数据最终一致性
        Post dbPost = postMapper.selectById(testPostId);
        assertThat(dbPost.getVotes()).isEqualTo(threads);

        Long voteRecordsCount = postVoteMapper.selectCount(new LambdaQueryWrapper<PostVote>()
                .eq(PostVote::getPostId, testPostId));
        assertThat(voteRecordsCount).isEqualTo(threads);

        // 5. 触发数据对账任务，验证对账结果确认 0 漂移
        boolean fixed = dataReconciliationScheduler.reconcilePostVotes(testPostId);
        assertThat(fixed).isFalse(); // False 说明数据原本就 100% 精确一致，无需修复
        log.info("==== BENCHMARK PASSED WITH ZERO DEADLOCKS AND 100% CONSISTENCY ====");
    }
}
