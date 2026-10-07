package com.luminous.api.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luminous.api.common.AfterCommit;
import com.luminous.api.common.BusinessException;
import com.luminous.api.post.Post;
import com.luminous.api.post.PostMapper;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.concurrent.*;

/** 使用共享短缓存，避免多实例本地副本失效；回源按节点限流并合并同一帖子请求。 */
@Service
public class PostCacheService {
    private static final Logger log = LoggerFactory.getLogger(PostCacheService.class);
    private static final int MAX_DATABASE_LOADS = 8;
    private final StringRedisTemplate redis;
    private final RedissonClient redisson;
    private final PostMapper posts;
    private final ObjectMapper mapper;
    private final Semaphore databaseSlots = new Semaphore(MAX_DATABASE_LOADS);
    private final ConcurrentMap<Long, CompletableFuture<Post>> loading = new ConcurrentHashMap<>();
    private volatile long redisRetryAfter;

    public PostCacheService(StringRedisTemplate redis, RedissonClient redisson, PostMapper posts, ObjectMapper mapper) {
        this.redis = redis; this.redisson = redisson; this.posts = posts;
        this.mapper = mapper.copy().findAndRegisterModules();
    }

    public Post queryWithPassThrough(Long id) {
        if (id == null) { return null; }
        // 事务中的快照不能写入共享缓存；权限与写操作直接读取数据库。
        boolean transactional = TransactionSynchronizationManager.isActualTransactionActive();
        if (!transactional && redisAvailable()) {
            try {
                String cached = redis.opsForValue().get(CacheConstants.POST_DETAIL_KEY + id);
                if (cached != null) { return cached.isEmpty() ? null : mapper.readValue(cached, Post.class); }
            } catch (Exception ex) { redisFailed(ex); }
        }
        if (transactional) { return loadDatabase(id); }
        CompletableFuture<Post> mine = new CompletableFuture<>();
        CompletableFuture<Post> existing = loading.putIfAbsent(id, mine);
        if (existing != null) {
            try {
                Post result = existing.get(250, TimeUnit.MILLISECONDS);
                // 返回副本，避免某个请求修改对象后影响其他等待者。
                return result == null ? null : mapper.convertValue(result, Post.class);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt(); throw busy();
            } catch (ExecutionException ex) {
                if (ex.getCause() instanceof RuntimeException cause) { throw cause; }
                throw busy();
            } catch (TimeoutException ex) { throw busy(); }
        }
        try {
            Post result = rebuild(id);
            mine.complete(result);
            return result == null ? null : mapper.convertValue(result, Post.class);
        } catch (RuntimeException ex) { mine.completeExceptionally(ex); throw ex; }
        finally { loading.remove(id, mine); }
    }

    private Post rebuild(Long id) {
        RLock lock = null;
        boolean acquired = false;
        try {
            if (redisAvailable()) {
                try {
                    lock = redisson.getLock(CacheConstants.LOCK_CACHE_REBUILD + id);
                    acquired = lock.tryLock(100, 5000, TimeUnit.MILLISECONDS);
                    String cached = redis.opsForValue().get(CacheConstants.POST_DETAIL_KEY + id);
                    if (cached != null) { return cached.isEmpty() ? null : mapper.readValue(cached, Post.class); }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt(); throw busy();
                } catch (BusinessException ex) { throw ex; }
                catch (Exception ex) { redisFailed(ex); }
            }
            // 有效期从数据库读取前开始计算，防止慢查询在删除后写回一个全新的旧缓存。
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(CacheConstants.POST_DETAIL_TTL_SECONDS);
            Post post = loadDatabase(id);
            writeCache(id, post, deadline);
            return post;
        } finally {
            if (acquired && lock != null) {
                try { if (lock.isHeldByCurrentThread()) { lock.unlock(); } }
                catch (Exception ex) { redisFailed(ex); }
            }
        }
    }

    private Post loadDatabase(Long id) {
        if (!databaseSlots.tryAcquire()) { throw busy(); }
        try { return posts.selectById(id); }
        finally { databaseSlots.release(); }
    }

    private BusinessException busy() {
        return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "POST_SOURCE_BUSY", "帖子服务繁忙，请稍后重试");
    }

    public void invalidatePost(Long id) {
        if (id == null) { return; }
        AfterCommit.run(() -> {
            try { redis.delete(CacheConstants.POST_DETAIL_KEY + id); }
            catch (Exception ex) { redisFailed(ex); }
        });
    }

    /** 预热仅触发重新读取，避免调用方持有的旧对象污染共享缓存。 */
    public void cachePost(Post post) {
        if (post != null && post.getId() != null) { queryWithPassThrough(post.getId()); }
    }

    private void writeCache(Long id, Post post, long deadline) {
        long remainingMillis = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
        if (!redisAvailable() || remainingMillis <= 0) { return; }
        try {
            redis.opsForValue().set(CacheConstants.POST_DETAIL_KEY + id,
                    post == null ? "" : mapper.writeValueAsString(post), remainingMillis, TimeUnit.MILLISECONDS);
        } catch (Exception ex) { redisFailed(ex); }
    }

    private boolean redisAvailable() { return System.nanoTime() >= redisRetryAfter; }
    private void redisFailed(Exception ex) {
        redisRetryAfter = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        log.warn("共享缓存或锁不可用，短暂熔断并限制数据库回源", ex);
    }
}
