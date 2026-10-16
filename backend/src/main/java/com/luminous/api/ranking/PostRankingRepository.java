package com.luminous.api.ranking;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.luminous.api.post.Post;
import com.luminous.api.post.PostMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.*;

/** 周期性聚合近期事实；窗口函数只把有界候选送入缓存，不在用户翻页时重算。 */
@Repository
public class PostRankingRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final PostMapper posts;

    public PostRankingRepository(NamedParameterJdbcTemplate jdbc, PostMapper posts) {
        this.jdbc = jdbc;
        this.posts = posts;
    }

    private static final String CANDIDATES_SQL = """
        WITH recent AS (
            SELECT id, author_id, community_id, created_at FROM posts
            WHERE is_public=TRUE AND status IN ('NORMAL','PUBLISHED')
              AND created_at>=:since AND created_at<=:now
              AND (:community IS NULL OR community_id=:community)
        ), ballots AS (
            SELECT v.post_id, SUM(v.desired_value) AS net_votes
            FROM vote_states v JOIN recent p ON p.id=v.post_id
            WHERE v.user_id<>p.author_id GROUP BY v.post_id
        ), discussions AS (
            SELECT c.post_id, LEAST(COUNT(DISTINCT c.author_id),10) AS participants
            FROM comments c JOIN recent p ON p.id=c.post_id
            WHERE c.author_id<>p.author_id AND c.author_id<>880000000000000001 GROUP BY c.post_id
        ), metrics AS (
            SELECT p.*, COALESCE(b.net_votes,0) AS net_votes, COALESCE(d.participants,0) AS participants
            FROM recent p LEFT JOIN ballots b ON b.post_id=p.id LEFT JOIN discussions d ON d.post_id=p.id
        ), scored AS (
            SELECT *, (1+GREATEST(net_votes,0)+0.25*participants)
                * CASE WHEN net_votes<0 THEN 0.05/(1-net_votes) ELSE 1 END
                / POWER(2+GREATEST(TIMESTAMPDIFF(SECOND,created_at,:now)/3600.0,0),1.1) AS recommendation_score
            FROM metrics
        ), ranked AS (
            SELECT *, ROW_NUMBER() OVER(ORDER BY recommendation_score DESC,created_at DESC,id DESC) AS recommend_rank,
                ROW_NUMBER() OVER(PARTITION BY (net_votes BETWEEN 0 AND 2 AND participants<=2 AND created_at>=:freshSince)
                    ORDER BY created_at DESC,id DESC) AS fresh_rank,
                ROW_NUMBER() OVER(ORDER BY net_votes DESC,participants DESC,created_at DESC,id DESC) AS week_rank,
                ROW_NUMBER() OVER(PARTITION BY (created_at>=:daySince)
                    ORDER BY net_votes DESC,participants DESC,created_at DESC,id DESC) AS day_rank
            FROM scored
        )
        SELECT id,author_id,community_id,created_at,net_votes,participants FROM ranked
        WHERE recommend_rank<=1500 OR week_rank<=500
           OR (fresh_rank<=500 AND net_votes BETWEEN 0 AND 2 AND participants<=2 AND created_at>=:freshSince)
           OR (created_at>=:daySince AND day_rank<=500)
        """;

    public List<PostRankingPolicy.Candidate> candidates(LocalDateTime at, Long communityId) {
        var parameters = new MapSqlParameterSource().addValue("since", at.minusDays(7))
                .addValue("daySince", at.minusHours(24)).addValue("freshSince", at.minusHours(48))
                .addValue("now", at).addValue("community", communityId);
        return jdbc.query(CANDIDATES_SQL, parameters, (row, index) -> new PostRankingPolicy.Candidate(
                row.getLong("id"), row.getLong("author_id"), row.getLong("community_id"),
                row.getTimestamp("created_at").toLocalDateTime(), row.getLong("net_votes"), row.getInt("participants")));
    }

    public Set<Long> joinedCommunities(long userId) {
        return new HashSet<>(jdbc.queryForList("SELECT community_id FROM community_members WHERE user_id=:user",
                Map.of("user", userId), Long.class));
    }

    /** 缓存仅保存编号；每页重新检查公开性与封禁状态，删除和封禁不等待榜单更新。 */
    public List<Post> visiblePosts(List<Long> ids) {
        if (ids.isEmpty()) return List.of();
        return posts.selectList(new LambdaQueryWrapper<Post>().in(Post::getId, ids)
                .eq(Post::getIsPublic, true).in(Post::getStatus, "NORMAL", "PUBLISHED"));
    }
}
