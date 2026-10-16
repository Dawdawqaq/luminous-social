package com.luminous.api.ranking;

import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/** 周期刷新公共候选；分布式锁与二次检查避免冷缓存时并发重复聚合。 */
@Service
public class PostRankingCandidates {
    private static final Logger log = LoggerFactory.getLogger(PostRankingCandidates.class);
    private final PostRankingCache cache;
    private final PostRankingRepository repository;
    private final RedissonClient redisson;
    private final Clock clock;

    public PostRankingCandidates(PostRankingCache cache, PostRankingRepository repository, RedissonClient redisson, Clock clock) {
        this.cache = cache; this.repository = repository; this.redisson = redisson; this.clock = clock;
    }

    public PostRankingCache.Batch current(Long communityId) {
        var cached = cache.batch(communityId);
        if (cached != null) return cached;
        return rebuild(communityId, false);
    }

    private PostRankingCache.Batch rebuild(Long communityId, boolean refresh) {
        var lock = redisson.getLock(cache.candidateKey(communityId) + ":lock");
        boolean locked = false;
        try {
            locked = lock.tryLock(1, TimeUnit.SECONDS);
            var cached = cache.batch(communityId);
            if (!locked) {
                if (cached != null) return cached;
                throw PostRankingCache.unavailable();
            }
            if (cached != null && (!refresh || cached.generatedAt().isAfter(LocalDateTime.now(clock).minusSeconds(110)))) return cached;
            var at = LocalDateTime.now(clock);
            var batch = new PostRankingCache.Batch(at, repository.candidates(at, communityId));
            cache.saveBatch(communityId, batch);
            return batch;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw PostRankingCache.unavailable();
        } catch (RuntimeException exception) {
            throw PostRankingCache.unavailable();
        } finally {
            try { if (locked && lock.isHeldByCurrentThread()) lock.unlock(); }
            catch (RuntimeException exception) { log.warn("榜单刷新锁释放失败，由锁租约自动回收", exception); }
        }
    }

    @Scheduled(fixedDelayString = "${app.ranking.refresh-ms:120000}", initialDelayString = "${app.ranking.initial-delay-ms:30000}")
    public void refresh() {
        try { rebuild(null, true); }
        catch (RuntimeException exception) { log.warn("榜单候选刷新失败，下次读取允许重试", exception); }
    }
}
