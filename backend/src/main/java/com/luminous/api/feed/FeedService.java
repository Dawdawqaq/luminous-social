package com.luminous.api.feed;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.luminous.api.auth.UserFollow;
import com.luminous.api.auth.UserFollowMapper;
import com.luminous.api.cache.CacheConstants;
import com.luminous.api.common.BusinessException;
import com.luminous.api.post.*;
import com.luminous.api.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/** 数据库统一拉取当前关注者的公开内容；收件箱仅作有界的可重建投影。 */
@Service
public class FeedService {
    private static final Logger log = LoggerFactory.getLogger(FeedService.class);
    public static final int HISTORY_DAYS = 30;
    public static final int INBOX_CAPACITY = 1000;
    public static final long INBOX_TTL_SECONDS = 7 * 24 * 3600;
    private static final byte[] INBOX_SCRIPT = ("redis.call('ZADD',KEYS[1],ARGV[1],ARGV[2]); "
            + "local excess=redis.call('ZCARD',KEYS[1])-tonumber(ARGV[3]); "
            + "if excess>0 then redis.call('ZREMRANGEBYRANK',KEYS[1],0,excess-1) end; "
            + "redis.call('EXPIRE',KEYS[1],ARGV[4]); return 1").getBytes(StandardCharsets.UTF_8);
    private final StringRedisTemplate redis;
    private final UserFollowMapper follows;
    private final PostMapper posts;
    private final PostService postService;
    public FeedService(StringRedisTemplate redis, UserFollowMapper follows, PostMapper posts, @Lazy PostService postService) {
        this.redis = redis; this.follows = follows; this.posts = posts; this.postService = postService;
    }

    public void fanOutToFollowers(Long authorId, Long postId) {
        try {
            Post post = posts.selectById(postId);
            if (post == null || !Boolean.TRUE.equals(post.getIsPublic()) || !Objects.equals(authorId, post.getAuthorId())) { return; }
            Long count = follows.selectCount(new LambdaQueryWrapper<UserFollow>().eq(UserFollow::getFollowingId, authorId));
            if (count != null && count > 10000) { return; }
            List<UserFollow> recipients = follows.selectList(new LambdaQueryWrapper<UserFollow>()
                    .eq(UserFollow::getFollowingId, authorId).last("LIMIT 10000"));
            if (recipients == null || recipients.isEmpty()) { return; }
            // 分值来自帖子创建时间，重投不会把历史帖子挪到最新位置。
            long score = post.getCreatedAt() == null ? System.currentTimeMillis() : score(post);
            redis.executePipelined((RedisCallback<Object>) connection -> {
                for (UserFollow recipient : recipients) {
                    connection.scriptingCommands().eval(INBOX_SCRIPT, org.springframework.data.redis.connection.ReturnType.INTEGER, 1,
                            bytes(CacheConstants.FEED_INBOX_KEY + recipient.getFollowerId()), bytes(String.valueOf(score)),
                            bytes(String.valueOf(postId)), bytes(String.valueOf(INBOX_CAPACITY)), bytes(String.valueOf(INBOX_TTL_SECONDS)));
                }
                return null;
            });
        } catch (Exception ex) { log.warn("动态收件箱投影失败，数据库拉取仍可展示帖子，postId={}", postId, ex); }
    }

    public FeedDtos.ScrollResult<PostDtos.PostDto> scrollFeed(CurrentUser user, Long lastScore, int offset, int size) {
        if (user == null) { throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录"); }
        com.luminous.api.common.PaginationPolicy.offset(offset);
        if (size < 1 || size > 100 || offset < 0 || (lastScore == null && offset != 0)
                || (lastScore != null && (lastScore < 0 || lastScore > 253402300799999L))) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_FEED_CURSOR", "动态分页参数无效");
        }
        long maximum = lastScore == null ? System.currentTimeMillis() : lastScore;
        ZoneId zone = ZoneId.systemDefault();
        LocalDateTime before = LocalDateTime.ofInstant(Instant.ofEpochMilli(maximum).plusMillis(1).minusNanos(1), zone);
        LocalDateTime since = LocalDateTime.now().minusDays(HISTORY_DAYS);
        // EXISTS 按当前关注关系过滤，一次查询合并普通作者和大 V，天然按帖子主键去重。
        List<Post> fetched = posts.selectFollowedFeed(user.id(), since, before, offset, size + 1);
        if (fetched == null || fetched.isEmpty()) { return new FeedDtos.ScrollResult<>(List.of(), lastScore, offset, false); }
        boolean hasMore = fetched.size() > size;
        List<Post> page = fetched.subList(0, Math.min(size, fetched.size()));
        long minimum = score(page.getLast());
        int atMinimum = (int) page.stream().filter(post -> score(post) == minimum).count();
        // 全页仍处于上一页的同毫秒分组时，累计已读取数量。
        int nextOffset = Objects.equals(lastScore, minimum) ? offset + atMinimum : atMinimum;
        return new FeedDtos.ScrollResult<>(postService.assemblePostDtos(page, user), minimum, nextOffset, hasMore);
    }

    /** 取消关注时直接清空该用户的可重建收件箱，防止历史条目残留。 */
    public void invalidateInbox(Long userId) {
        try { redis.delete(CacheConstants.FEED_INBOX_KEY + userId); }
        catch (Exception ex) { log.warn("清理动态收件箱失败，读取仍按当前关注关系过滤，userId={}", userId, ex); }
    }

    private static long score(Post post) { return post.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(); }
    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
}
