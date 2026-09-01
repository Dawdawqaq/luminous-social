package com.luminous.api.feed;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.luminous.api.auth.UserFollow;
import com.luminous.api.auth.UserFollowMapper;
import com.luminous.api.cache.CacheConstants;
import com.luminous.api.post.Post;
import com.luminous.api.post.PostDtos;
import com.luminous.api.post.PostMapper;
import com.luminous.api.post.PostService;
import com.luminous.api.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FeedService {
    private static final Logger log = LoggerFactory.getLogger(FeedService.class);
    
    private final StringRedisTemplate stringRedisTemplate;
    private final UserFollowMapper userFollowMapper;
    private final PostMapper postMapper;
    private final PostService postService;
    
    public FeedService(
            StringRedisTemplate stringRedisTemplate,
            UserFollowMapper userFollowMapper,
            PostMapper postMapper,
            @Lazy PostService postService
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.userFollowMapper = userFollowMapper;
        this.postMapper = postMapper;
        this.postService = postService;
    }
    
    public void fanOutToFollowers(Long authorId, Long postId) {
        try {
            Long followerCount = userFollowMapper.selectCount(
                new LambdaQueryWrapper<UserFollow>().eq(UserFollow::getFollowingId, authorId)
            );
            
            if (followerCount != null && followerCount > 10000) {
                // 大V账号采用拉模式 (Pull model)，跳过全量推扩散以防写放大
                log.info("Author {} has >10000 followers, skipping fan-out-on-write", authorId);
                return;
            }
            
            List<UserFollow> followers = userFollowMapper.selectList(
                new LambdaQueryWrapper<UserFollow>().eq(UserFollow::getFollowingId, authorId)
            );
            if (followers == null || followers.isEmpty()) {
                return;
            }
            
            long timestamp = System.currentTimeMillis();
            String postIdStr = String.valueOf(postId);
            
            // 使用 Redis Pipeline 批量管道执行 ZADD，将 N 次网络往返降为 1 次
            stringRedisTemplate.executePipelined((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
                for (UserFollow follower : followers) {
                    String key = CacheConstants.FEED_INBOX_KEY + follower.getFollowerId();
                    connection.zSetCommands().zAdd(key.getBytes(), (double) timestamp, postIdStr.getBytes());
                }
                return null;
            });
            log.info("Successfully fanned out post {} to {} followers via Redis Pipeline", postId, followers.size());
        } catch (Exception e) {
            log.warn("Failed to fan out post {} to followers of {}", postId, authorId, e);
        }
    }
    
    public FeedDtos.ScrollResult<PostDtos.PostDto> scrollFeed(CurrentUser currentUser, Long lastScore, int offset, int size) {
        String key = CacheConstants.FEED_INBOX_KEY + currentUser.id();
        double maxScore = lastScore == null ? System.currentTimeMillis() : lastScore;
        
        Set<ZSetOperations.TypedTuple<String>> tuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, maxScore, offset, size);
                
        if (tuples == null || tuples.isEmpty()) {
            return new FeedDtos.ScrollResult<>(List.of(), lastScore, offset, false);
        }
        
        List<Long> postIds = new ArrayList<>();
        long minScore = 0;
        int nextOffset = offset;
        
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            if (tuple.getValue() != null) {
                postIds.add(Long.valueOf(tuple.getValue()));
            }
            long score = tuple.getScore() != null ? tuple.getScore().longValue() : 0;
            if (score == minScore) {
                nextOffset++;
            } else {
                minScore = score;
                nextOffset = 1;
            }
        }
        
        if (postIds.isEmpty()) {
            return new FeedDtos.ScrollResult<>(List.of(), minScore, nextOffset, false);
        }
        
        List<Post> posts = postMapper.selectBatchIds(postIds);
        // 保留 Redis SortedSet 的时间降序排序
        List<Post> orderedPosts = new ArrayList<>();
        for (Long pid : postIds) {
            posts.stream().filter(p -> p.getId().equals(pid)).findFirst().ifPresent(orderedPosts::add);
        }
        
        // 彻底消除 N+1 查询：复用 PostService.assemblePostDtos 执行批量组装
        List<PostDtos.PostDto> items = postService.assemblePostDtos(orderedPosts, currentUser);
                
        boolean hasMore = postIds.size() >= size;
        
        return new FeedDtos.ScrollResult<>(items, minScore, nextOffset, hasMore);
    }
}
