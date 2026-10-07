package com.luminous.api.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luminous.api.common.BusinessException;
import com.luminous.api.post.*;
import org.junit.jupiter.api.*;
import org.redisson.api.*;
import org.springframework.data.redis.core.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PostCacheServiceTest {
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    ValueOperations<String, String> values = mock(ValueOperations.class);
    RedissonClient redisson = mock(RedissonClient.class);
    PostMapper posts = mock(PostMapper.class);
    PostCacheService service;
    @BeforeEach void setUp() {
        when(redis.opsForValue()).thenReturn(values);
        service = new PostCacheService(redis, redisson, posts, new ObjectMapper());
    }
    @Test void redisAndLockOutageNeverExceedsEightDatabaseLoads() throws Exception {
        when(values.get(anyString())).thenThrow(new IllegalStateException("Redis 停机"));
        CountDownLatch entered = new CountDownLatch(8), release = new CountDownLatch(1);
        AtomicInteger running = new AtomicInteger(), maximum = new AtomicInteger();
        when(posts.selectById(anyLong())).thenAnswer(call -> {
            int concurrent = running.incrementAndGet(); maximum.accumulateAndGet(concurrent, Math::max); entered.countDown();
            try { if (!release.await(5, TimeUnit.SECONDS)) { throw new IllegalStateException("回源等待超时"); } return new Post(); }
            finally { running.decrementAndGet(); }
        });
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<Post>> futures = new ArrayList<>();
        try {
            for (long id = 1; id <= 8; id++) { long postId = id; futures.add(pool.submit(() -> service.queryWithPassThrough(postId))); }
            assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> service.queryWithPassThrough(9L)).isInstanceOfSatisfying(BusinessException.class,
                    ex -> assertThat(ex.getStatus().value()).isEqualTo(503));
            assertThat(maximum.get()).isEqualTo(8);
            release.countDown();
            for (Future<Post> future : futures) { assertThat(future.get(2, TimeUnit.SECONDS)).isNotNull(); }
        } finally { release.countDown(); pool.shutdownNow(); }
        verifyNoInteractions(redisson);
    }
    @Test void redissonAcquisitionFailureFallsBackAndReleaseFailureDoesNotLoseResult() throws Exception {
        when(redisson.getLock(anyString())).thenThrow(new IllegalStateException("Redisson 不可用"));
        Post post = new Post(); post.setId(1L);
        when(posts.selectById(1L)).thenReturn(post);
        assertThat(service.queryWithPassThrough(1L).getId()).isEqualTo(1L);
        reset(redisson);
        RLock lock = mock(RLock.class);
        when(redisson.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(100, 5000, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenThrow(new IllegalStateException("释放时 Redis 停机"));
        PostCacheService another = new PostCacheService(redis, redisson, posts, new ObjectMapper());
        assertThat(another.queryWithPassThrough(1L).getId()).isEqualTo(1L);
    }
    @Test void simultaneousSamePostQueriesShareOneDatabaseLoad() throws Exception {
        when(values.get(anyString())).thenThrow(new IllegalStateException("Redis 停机"));
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        Post post = new Post(); post.setId(1L);
        when(posts.selectById(1L)).thenAnswer(call -> { entered.countDown(); release.await(2, TimeUnit.SECONDS); return post; });
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Post> first = pool.submit(() -> service.queryWithPassThrough(1L));
            assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
            Future<Post> second = pool.submit(() -> service.queryWithPassThrough(1L));
            Thread.sleep(50); release.countDown();
            assertThat(first.get(1, TimeUnit.SECONDS).getId()).isEqualTo(1L);
            assertThat(second.get(1, TimeUnit.SECONDS).getId()).isEqualTo(1L);
            verify(posts, times(1)).selectById(1L);
        } finally { release.countDown(); pool.shutdownNow(); }
    }
}
