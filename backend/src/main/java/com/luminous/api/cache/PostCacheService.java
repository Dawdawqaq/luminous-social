package com.luminous.api.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.luminous.api.post.Post;
import com.luminous.api.post.PostMapper;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Service
public class PostCacheService {

    private static final Logger log = LoggerFactory.getLogger(PostCacheService.class);
    
    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;
    private final PostMapper postMapper;
    private final ObjectMapper objectMapper;
    
    private final Cache<Long, Post> localCache;

    public PostCacheService(StringRedisTemplate stringRedisTemplate,
                            RedissonClient redissonClient,
                            PostMapper postMapper,
                            ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.redissonClient = redissonClient;
        this.postMapper = postMapper;
        this.objectMapper = objectMapper.copy().findAndRegisterModules();
        
        this.localCache = Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(30, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Query post with three-level cache protection (penetration, avalanche, breakdown)
     * 使用有界重试迭代循环代替无界递归，防止极端高并发下栈溢出
     */
    public Post queryWithPassThrough(Long id) {
        if (id == null) return null;

        // L1: Check Caffeine local cache
        Post post = localCache.getIfPresent(id);
        if (post != null) {
            return post;
        }

        String redisKey = CacheConstants.POST_DETAIL_KEY + id;
        
        // 最大重试 3 次以等待互斥锁重建缓存
        for (int retry = 0; retry < 3; retry++) {
            // L2: Check Redis
            String json = null;
            try {
                json = stringRedisTemplate.opsForValue().get(redisKey);
            } catch (Exception e) {
                log.warn("Failed to get post {} from Redis", id, e);
            }

            if (json != null) {
                if (json.isEmpty()) {
                    // Penetration protection hit (空值缓存拦截)
                    return null;
                }
                post = deserialize(json);
                if (post != null) {
                    localCache.put(id, post);
                    return post;
                }
            }

            // L3: Cache miss -> acquire Redisson lock to prevent cache breakdown
            String lockKey = CacheConstants.LOCK_CACHE_REBUILD + id;
            RLock lock = redissonClient.getLock(lockKey);
            boolean isLockAcquired = false;
            try {
                isLockAcquired = lock.tryLock(2, 5, TimeUnit.SECONDS);
                if (!isLockAcquired) {
                    // 未获取到互斥锁，等待 50ms 后进入下一次循环尝试读取其他线程已重建好的缓存
                    Thread.sleep(50);
                    continue;
                }

                // Double check Redis after acquiring lock
                try {
                    json = stringRedisTemplate.opsForValue().get(redisKey);
                } catch (Exception e) {
                    log.warn("Failed to get post {} from Redis during double check", id, e);
                }
                if (json != null) {
                    if (json.isEmpty()) {
                        return null;
                    }
                    post = deserialize(json);
                    if (post != null) {
                        localCache.put(id, post);
                        return post;
                    }
                }

                // Query DB
                post = postMapper.selectById(id);
                if (post == null) {
                    // Cache empty string to prevent penetration
                    try {
                        stringRedisTemplate.opsForValue().set(redisKey, "", CacheConstants.POST_DETAIL_NULL_TTL_MINUTES, TimeUnit.MINUTES);
                    } catch (Exception e) {
                        log.warn("Failed to cache null post {} in Redis", id, e);
                    }
                    return null;
                }

                // Cache in Redis with random TTL to prevent avalanche
                cachePost(post);
                // Put in L1 cache
                localCache.put(id, post);
                
                return post;

            } catch (InterruptedException e) {
                log.error("Thread interrupted while waiting for lock", e);
                Thread.currentThread().interrupt();
                break;
            } finally {
                if (isLockAcquired && lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        }

        // 降级：重试耗尽直接回源数据库并返回
        Post fallback = postMapper.selectById(id);
        if (fallback != null) {
            localCache.put(id, fallback);
        }
        return fallback;
    }

    /**
     * Delayed double delete strategy for invalidating cache
     */
    public void invalidatePost(Long id) {
        String redisKey = CacheConstants.POST_DETAIL_KEY + id;
        try {
            stringRedisTemplate.delete(redisKey);
        } catch (Exception e) {
            log.warn("Failed to delete post {} from Redis", id, e);
        }
        
        localCache.invalidate(id);
        
        CompletableFuture.delayedExecutor(500, TimeUnit.MILLISECONDS).execute(() -> {
            try {
                stringRedisTemplate.delete(redisKey);
            } catch (Exception e) {
                log.warn("Failed to delayed delete post {} from Redis", id, e);
            }
        });
    }

    /**
     * Cache post in Redis with random TTL
     */
    public void cachePost(Post post) {
        if (post == null || post.getId() == null) {
            return;
        }
        String redisKey = CacheConstants.POST_DETAIL_KEY + post.getId();
        String json = serialize(post);
        if (json != null) {
            long randomMinutes = ThreadLocalRandom.current().nextLong(11); // 0 to 10
            long ttl = CacheConstants.POST_DETAIL_TTL_MINUTES + randomMinutes;
            try {
                stringRedisTemplate.opsForValue().set(redisKey, json, ttl, TimeUnit.MINUTES);
            } catch (Exception e) {
                log.warn("Failed to cache post {} in Redis", post.getId(), e);
            }
        }
    }

    private String serialize(Post post) {
        try {
            return objectMapper.writeValueAsString(post);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize post {}", post.getId(), e);
            return null;
        }
    }

    private Post deserialize(String json) {
        try {
            return objectMapper.readValue(json, Post.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize post", e);
            return null;
        }
    }
}
