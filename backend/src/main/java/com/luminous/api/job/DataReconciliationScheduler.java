package com.luminous.api.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.luminous.api.cache.CacheConstants;
import com.luminous.api.post.Post;
import com.luminous.api.post.PostMapper;
import com.luminous.api.post.PostVote;
import com.luminous.api.post.PostVoteMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 生产级数据定时对账与自愈补偿任务
 * 解决极端场景下（Redis/MQ 宕机、网络丢包等）造成的 Redis 缓存计数与 MySQL 数据库数据微量漂移
 */
@Component
public class DataReconciliationScheduler {
    private static final Logger log = LoggerFactory.getLogger(DataReconciliationScheduler.class);

    private final PostMapper postMapper;
    private final PostVoteMapper postVoteMapper;
    private final StringRedisTemplate redisTemplate;

    public DataReconciliationScheduler(
            PostMapper postMapper,
            PostVoteMapper postVoteMapper,
            StringRedisTemplate redisTemplate
    ) {
        this.postMapper = postMapper;
        this.postVoteMapper = postVoteMapper;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 每 15 分钟定时扫描近 2 小时活跃的帖子进行数据对账
     */
    @Scheduled(cron = "0 0/15 * * * ?")
    public void reconcileActivePosts() {
        log.info("Starting scheduled data reconciliation for active posts...");
        LocalDateTime twoHoursAgo = LocalDateTime.now().minusHours(2);

        List<Post> activePosts = postMapper.selectList(new LambdaQueryWrapper<Post>()
                .ge(Post::getUpdatedAt, twoHoursAgo)
                .orderByDesc(Post::getUpdatedAt)
                .last("LIMIT 500"));

        int reconciledCount = 0;
        for (Post post : activePosts) {
            boolean fixed = reconcilePostVotes(post.getId());
            if (fixed) {
                reconciledCount++;
            }
        }
        log.info("Finished data reconciliation: checked {} posts, healed {} discrepancies.", activePosts.size(), reconciledCount);
    }

    /**
     * 单个帖子数据对账与自动自愈
     * @return true 若发现并修复了不一致；false 若数据原本即一致
     */
    public boolean reconcilePostVotes(Long postId) {
        if (postId == null) return false;

        try {
            // 1. 获取 MySQL 权威总计
            List<PostVote> votes = postVoteMapper.selectList(new LambdaQueryWrapper<PostVote>()
                    .eq(PostVote::getPostId, postId));
            int authoritativeCount = votes.stream().mapToInt(PostVote::getValue).sum();

            // 2. 获取 Redis 缓存计数
            String key = "stat:post:votes:" + postId;
            String redisValStr = redisTemplate.opsForValue().get(key);

            if (redisValStr != null) {
                int redisCount = Integer.parseInt(redisValStr);
                if (redisCount != authoritativeCount) {
                    log.warn("Data drift detected for post {}! Redis={}, MySQL authoritative={}. Healing Redis cache...",
                            postId, redisCount, authoritativeCount);
                    redisTemplate.opsForValue().set(key, String.valueOf(authoritativeCount));
                    return true;
                }
            } else {
                // Redis 缓存不存在，顺便回填
                redisTemplate.opsForValue().set(key, String.valueOf(authoritativeCount));
            }
        } catch (Exception e) {
            log.error("Failed to reconcile votes for post {}", postId, e);
        }
        return false;
    }
}
