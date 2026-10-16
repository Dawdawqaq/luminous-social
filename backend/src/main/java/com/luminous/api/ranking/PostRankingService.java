package com.luminous.api.ranking;

import com.luminous.api.common.BusinessException;
import com.luminous.api.community.CommunityService;
import com.luminous.api.post.Post;
import com.luminous.api.post.PostDtos;
import com.luminous.api.post.PostService;
import com.luminous.api.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 创建一次阅读排序，后续按编号位置推进；实时可见性过滤不会改变已读位置。 */
@Service
public class PostRankingService {
    public record Slice(List<PostDtos.PostDto> items, String nextCursor, boolean hasMore, long generatedAt, long expiresAt) {}
    private record Cursor(String snapshotId, int offset) {}
    private final PostRankingCandidates candidates;
    private final PostRankingCache cache;
    private final PostRankingRepository repository;
    private final PostService posts;
    private final CommunityService communities;
    private final Clock clock;

    public PostRankingService(PostRankingCandidates candidates, PostRankingCache cache, PostRankingRepository repository,
                              PostService posts, CommunityService communities, Clock clock) {
        this.candidates = candidates; this.cache = cache; this.repository = repository;
        this.posts = posts; this.communities = communities; this.clock = clock;
    }

    public Slice list(String type, String period, String community, String cursor, int size, CurrentUser viewer) {
        if (type == null || period == null || !Set.of("recommend", "hot").contains(type) || !Set.of("day", "week").contains(period)
                || ("recommend".equals(type) && !"day".equals(period)) || size < 1 || size > 30
                || (community != null && community.length() > 64)) throw invalid();
        String scope = community == null ? "" : community.trim();
        long viewerId = viewer == null ? 0 : viewer.id();
        PostRankingCache.Snapshot snapshot;
        Cursor position;
        if (cursor == null || cursor.isBlank()) {
            Long communityId = scope.isEmpty() ? null : communities.requireBySlug(scope).getId();
            var batch = candidates.current(communityId);
            Set<Long> joined = viewer == null || "hot".equals(type) ? Set.of() : repository.joinedCommunities(viewerId);
            // 同一候选版本、用户及兴趣复用快照，避免高频刷新反复创建五百条编号列表。
            String identity = batch.version() + "|" + viewerId + "|" + type + "|" + period + "|" + scope + "|"
                    + joined.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
            position = new Cursor(UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)).toString().replace("-", ""), 0);
            snapshot = cache.snapshot(position.snapshotId());
            if (snapshot == null || snapshot.expiresAt() <= clock.millis()) {
                List<Long> ids = "hot".equals(type) ? PostRankingPolicy.hot(batch.candidates(), batch.generatedAt(), period)
                        : PostRankingPolicy.recommend(batch.candidates(), batch.generatedAt(), joined);
                long generatedAt = batch.generatedAt().atZone(clock.getZone()).toInstant().toEpochMilli();
                snapshot = new PostRankingCache.Snapshot(viewerId, type, period, scope, generatedAt,
                        clock.millis() + PostRankingCache.SNAPSHOT_TTL.toMillis(), ids);
                if (!ids.isEmpty()) cache.saveSnapshot(position.snapshotId(), snapshot);
            }
        } else {
            position = decode(cursor);
            snapshot = cache.snapshot(position.snapshotId());
            if (snapshot == null || snapshot.expiresAt() <= clock.millis()) {
                throw new BusinessException(HttpStatus.GONE, "RANKING_SNAPSHOT_EXPIRED", "这份榜单已过期，请刷新列表后继续浏览");
            }
            if (snapshot.viewerId() != viewerId || !snapshot.type().equals(type) || !snapshot.period().equals(period)
                    || !snapshot.community().equals(scope) || position.offset() > snapshot.ids().size()) throw invalid();
        }
        return page(snapshot, position, size, viewer);
    }

    private Slice page(PostRankingCache.Snapshot snapshot, Cursor cursor, int size, CurrentUser viewer) {
        List<Post> visible = new ArrayList<>();
        int index = cursor.offset(), nextOffset = index;
        boolean hasMore = false;
        while (index < snapshot.ids().size() && !hasMore) {
            int end = Math.min(index + Math.max(20, size + 1), snapshot.ids().size());
            var chunk = snapshot.ids().subList(index, end);
            Map<Long, Post> current = repository.visiblePosts(chunk).stream().collect(Collectors.toMap(Post::getId, Function.identity()));
            while (index < end) {
                Post post = current.get(snapshot.ids().get(index));
                if (post != null) {
                    if (visible.size() == size) { hasMore = true; break; }
                    visible.add(post);
                    nextOffset = index + 1;
                }
                index++;
            }
        }
        return new Slice(posts.assemblePostDtos(visible, viewer), hasMore ? cursor.snapshotId() + "." + nextOffset : null,
                hasMore, snapshot.generatedAt(), snapshot.expiresAt());
    }

    private static Cursor decode(String text) {
        if (!text.matches("[a-f0-9]{32}\\.[0-9]{1,3}")) throw invalid();
        int separator = text.indexOf('.');
        int offset = Integer.parseInt(text.substring(separator + 1));
        if (offset < 0 || offset > PostRankingPolicy.WINDOW_LIMIT) throw invalid();
        return new Cursor(text.substring(0, separator), offset);
    }

    private static BusinessException invalid() {
        return new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_RANKING_REQUEST", "榜单参数或加载位置无效，请刷新后重试");
    }
}
