package com.luminous.api.cache;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.luminous.api.auth.UserFollowMapper;
import com.luminous.api.post.PostMapper;
import com.luminous.api.post.Post;
import com.luminous.api.auth.UserFollow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class UserStatsCacheService {

    private static final Logger log = LoggerFactory.getLogger(UserStatsCacheService.class);
    
    private final StringRedisTemplate stringRedisTemplate;
    private final PostMapper postMapper;
    private final UserFollowMapper userFollowMapper;

    public record Stats(long posts, long followers, long following) {}

    public UserStatsCacheService(StringRedisTemplate stringRedisTemplate,
                                 PostMapper postMapper,
                                 UserFollowMapper userFollowMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.postMapper = postMapper;
        this.userFollowMapper = userFollowMapper;
    }

    public Stats getUserStats(Long userId) {
        String redisKey = CacheConstants.USER_STATS_KEY + userId;
        try {
            List<Object> values = stringRedisTemplate.opsForHash().multiGet(redisKey, List.of("posts", "followers", "following"));
            if (values.size() == 3 && values.get(0) != null && values.get(1) != null && values.get(2) != null) {
                return new Stats(
                        Long.parseLong(values.get(0).toString()),
                        Long.parseLong(values.get(1).toString()),
                        Long.parseLong(values.get(2).toString())
                );
            }
        } catch (Exception e) {
            log.warn("Failed to get user stats for user {} from Redis", userId, e);
        }

        // Query DB
        long posts = 0;
        long followers = 0;
        long following = 0;
        
        try {
            posts = postMapper.selectCount(new LambdaQueryWrapper<Post>().eq(Post::getAuthorId, userId));
            followers = userFollowMapper.selectCount(new LambdaQueryWrapper<UserFollow>().eq(UserFollow::getFollowingId, userId));
            following = userFollowMapper.selectCount(new LambdaQueryWrapper<UserFollow>().eq(UserFollow::getFollowerId, userId));
        } catch (Exception e) {
            log.error("Failed to query DB for user stats {}", userId, e);
        }

        // Populate Redis Hash
        try {
            stringRedisTemplate.opsForHash().putAll(redisKey, java.util.Map.of(
                    "posts", String.valueOf(posts),
                    "followers", String.valueOf(followers),
                    "following", String.valueOf(following)
            ));
            stringRedisTemplate.expire(redisKey, 10, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("Failed to cache user stats for user {}", userId, e);
        }

        return new Stats(posts, followers, following);
    }

    public void incrementPostCount(Long userId) {
        incrementHashField(userId, "posts", 1);
    }

    public void decrementPostCount(Long userId) {
        incrementHashField(userId, "posts", -1);
    }

    public void incrementFollowerCount(Long userId) {
        incrementHashField(userId, "followers", 1);
    }

    public void decrementFollowerCount(Long userId) {
        incrementHashField(userId, "followers", -1);
    }

    public void incrementFollowingCount(Long userId) {
        incrementHashField(userId, "following", 1);
    }

    public void decrementFollowingCount(Long userId) {
        incrementHashField(userId, "following", -1);
    }

    public void invalidateUserStats(Long userId) {
        String redisKey = CacheConstants.USER_STATS_KEY + userId;
        try {
            stringRedisTemplate.delete(redisKey);
        } catch (Exception e) {
            log.warn("Failed to invalidate user stats for user {}", userId, e);
        }
    }
    
    private static final org.springframework.data.redis.core.script.DefaultRedisScript<Long> HINCRBY_IF_EXISTS_SCRIPT;

    static {
        HINCRBY_IF_EXISTS_SCRIPT = new org.springframework.data.redis.core.script.DefaultRedisScript<>();
        HINCRBY_IF_EXISTS_SCRIPT.setScriptText(
                "if redis.call('EXISTS', KEYS[1]) == 1 then " +
                "    return redis.call('HINCRBY', KEYS[1], ARGV[1], ARGV[2]) " +
                "else " +
                "    return nil " +
                "end"
        );
        HINCRBY_IF_EXISTS_SCRIPT.setResultType(Long.class);
    }

    private void incrementHashField(Long userId, String field, long delta) {
        String redisKey = CacheConstants.USER_STATS_KEY + userId;
        try {
            // 原子执行：只有当 Key 存在时才递增，彻底杜绝 Key 过期导致的残缺 Hash 写入
            stringRedisTemplate.execute(
                    HINCRBY_IF_EXISTS_SCRIPT,
                    List.of(redisKey),
                    field,
                    String.valueOf(delta)
            );
        } catch (Exception e) {
            log.warn("Failed to atomically modify user stats for user {}", userId, e);
        }
    }
}
