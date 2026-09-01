package com.luminous.api.cache;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.luminous.api.post.Post;
import com.luminous.api.post.PostMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CacheWarmupRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CacheWarmupRunner.class);

    private final PostMapper postMapper;
    private final PostCacheService postCacheService;

    public CacheWarmupRunner(PostMapper postMapper, PostCacheService postCacheService) {
        this.postMapper = postMapper;
        this.postCacheService = postCacheService;
    }

    @Override
    public void run(String... args) {
        try {
            log.info("Starting cache warmup...");
            List<Post> topPosts = postMapper.selectList(
                    new LambdaQueryWrapper<Post>()
                            .orderByDesc(Post::getVotes)
                            .last("LIMIT 100")
            );

            for (Post post : topPosts) {
                postCacheService.cachePost(post);
            }

            log.info("Cache warmup completed. Pre-loaded {} posts into cache.", topPosts.size());
        } catch (Exception e) {
            log.error("Cache warmup failed. Startup will continue.", e);
        }
    }
}
