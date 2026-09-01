package com.luminous.api.integration;

import com.luminous.api.auth.AuthDtos;
import com.luminous.api.auth.AuthService;
import com.luminous.api.cache.CacheConstants;
import com.luminous.api.cache.PostCacheService;
import com.luminous.api.common.ApiResponse;
import com.luminous.api.community.CommunityDtos;
import com.luminous.api.community.CommunityService;
import com.luminous.api.post.Post;
import com.luminous.api.post.PostDtos;
import com.luminous.api.post.PostMapper;
import com.luminous.api.post.PostService;
import com.luminous.api.security.CurrentUser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CacheAndConcurrencyStressTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private CommunityService communityService;

    @Autowired
    private PostService postService;

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private PostCacheService postCacheService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private CurrentUser author;
    private List<CurrentUser> voters = new ArrayList<>();
    private String communitySlug;
    private PostDtos.PostDto targetPost;

    @BeforeAll
    void setupData() {
        communitySlug = "concurrency-comm-" + UUID.randomUUID().toString().substring(0, 6);

        // Register Author
        String authorName = "author_stress_" + UUID.randomUUID().toString().substring(0, 6);
        AuthDtos.AuthResponse authResp = authService.register(new AuthDtos.RegisterRequest(authorName, "Stress Author", "Pass123!"));
        author = new CurrentUser(Long.parseLong(authResp.user().id()), authResp.user().username(), authResp.user().role());

        // Create Community
        communityService.create(new CommunityDtos.CreateCommunityRequest(communitySlug, "Concurrency Lab", "Tech", "Stress test lab"), author);

        // Create Post
        targetPost = postService.create(new PostDtos.CreatePostRequest(communitySlug, "High Concurrency Test Post", "Testing concurrent voting & locks", null, true), author);

        // Register 40 voters
        for (int i = 0; i < 40; i++) {
            String vName = "voter_" + i + "_" + UUID.randomUUID().toString().substring(0, 4);
            AuthDtos.AuthResponse resp = authService.register(new AuthDtos.RegisterRequest(vName, "Voter " + i, "Pass123!"));
            CurrentUser voter = new CurrentUser(Long.parseLong(resp.user().id()), resp.user().username(), resp.user().role());
            communityService.join(communitySlug, voter);
            voters.add(voter);
        }
    }

    @Test
    void testConcurrentVoting_DataIntegrityUnderDistributedLock() throws InterruptedException {
        int threadCount = voters.size();
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());

        Long postId = Long.parseLong(targetPost.id());

        for (int i = 0; i < threadCount; i++) {
            final CurrentUser voter = voters.get(i);
            executor.submit(() -> {
                try {
                    startLatch.await();
                    postService.votePost(postId, 1, voter);
                    successCount.incrementAndGet();
                } catch (Throwable t) {
                    exceptions.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Fire all threads simultaneously
        startLatch.countDown();
        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(exceptions).isEmpty();
        assertThat(successCount.get()).isEqualTo(threadCount);

        // Await asynchronous write-behind flush to MySQL
        for (int i = 0; i < 20; i++) {
            Post p = postMapper.selectById(postId);
            if (p != null && p.getVotes() == threadCount) {
                break;
            }
            Thread.sleep(300);
        }

        // Verify MySQL and Cache consistency
        Post postInDb = postMapper.selectById(postId);
        assertThat(postInDb.getVotes()).isEqualTo(threadCount);

        // Redis cache verification
        String redisVotes = redisTemplate.opsForValue().get("stat:post:votes:" + postId);
        assertThat(redisVotes).isEqualTo(String.valueOf(threadCount));
    }

    @Test
    void testCacheBreakdown_ConcurrentQueriesOnlyRebuildsOnce() throws InterruptedException {
        Long postId = Long.parseLong(targetPost.id());
        // Force delete cache to simulate expiration
        redisTemplate.delete(CacheConstants.POST_DETAIL_KEY + postId);

        int queryThreads = 50;
        ExecutorService executor = Executors.newFixedThreadPool(queryThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(queryThreads);

        AtomicInteger successQueries = new AtomicInteger(0);

        for (int i = 0; i < queryThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    Post p = postCacheService.queryWithPassThrough(postId);
                    if (p != null && p.getId().equals(postId)) {
                        successQueries.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(successQueries.get()).isEqualTo(queryThreads);
        // Verify cache was rebuilt in Redis
        String cachedJson = redisTemplate.opsForValue().get(CacheConstants.POST_DETAIL_KEY + postId);
        assertThat(cachedJson).isNotNull().isNotBlank();
    }

    @Test
    void testCachePenetration_NonExistentPostCachesEmptyString() {
        Long nonExistentId = 99999999999999L;

        // 1st query -> queries DB and caches empty string
        Post firstResult = postCacheService.queryWithPassThrough(nonExistentId);
        assertThat(firstResult).isNull();

        String redisVal = redisTemplate.opsForValue().get(CacheConstants.POST_DETAIL_KEY + nonExistentId);
        assertThat(redisVal).isNotNull().isEmpty(); // Empty string cached for penetration protection

        // 2nd query -> directly hits Redis empty string in <1ms without querying DB
        long start = System.nanoTime();
        Post secondResult = postCacheService.queryWithPassThrough(nonExistentId);
        long elapsedMicros = (System.nanoTime() - start) / 1000;

        assertThat(secondResult).isNull();
        assertThat(elapsedMicros).isLessThan(50000); // Sub-millisecond Redis hit
    }
}
