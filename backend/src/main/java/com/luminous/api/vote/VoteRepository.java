package com.luminous.api.vote;

import com.luminous.api.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/** 投票意图与水位存储；所有修改统一在帖子行锁保护下执行。 */
@Repository
public class VoteRepository {
    public record State(long postId, long userId, int desiredValue, long acceptedVersion,
                        int appliedValue, long appliedVersion) {}
    public record Clock(long postId, long acceptedVersion, int acceptedTotal) {}
    private final JdbcTemplate jdbc;

    public VoteRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void lockPost(long postId) {
        if (!tryLockPost(postId)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "POST_NOT_FOUND", "帖子不存在");
        }
    }

    public boolean tryLockPost(long postId) {
        return !jdbc.queryForList("SELECT id FROM posts WHERE id=? FOR UPDATE", Long.class, postId).isEmpty();
    }

    public Clock initialize(long postId) {
        Clock clock = clock(postId);
        if (clock != null) { return clock; }
        // 外层事务可能已经建立旧快照；基线和水位必须使用锁定读取获取最新提交状态。
        int total = Math.toIntExact(jdbc.queryForList("SELECT value FROM post_votes WHERE post_id=? FOR UPDATE", Integer.class, postId)
                .stream().mapToLong(Integer::longValue).sum());
        jdbc.update("INSERT INTO post_vote_clocks(post_id,accepted_version,accepted_total) VALUES(?,0,?)", postId, total);
        jdbc.update("INSERT INTO vote_states(post_id,user_id,desired_value,accepted_version,applied_value,applied_version) "
                + "SELECT post_id,user_id,value,0,value,0 FROM post_votes WHERE post_id=?", postId);
        return new Clock(postId, 0, total);
    }

    public Clock clock(long postId) {
        List<Clock> rows = jdbc.query("SELECT * FROM post_vote_clocks WHERE post_id=? FOR UPDATE",
                (rs, row) -> new Clock(rs.getLong("post_id"), rs.getLong("accepted_version"), rs.getInt("accepted_total")), postId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public State state(long postId, long userId) {
        List<State> rows = jdbc.query("SELECT * FROM vote_states WHERE post_id=? AND user_id=? FOR UPDATE", this::mapState, postId, userId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public List<State> states(long postId) {
        return jdbc.query("SELECT * FROM vote_states WHERE post_id=? ORDER BY user_id FOR UPDATE", this::mapState, postId);
    }

    private State mapState(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new State(rs.getLong("post_id"), rs.getLong("user_id"), rs.getInt("desired_value"),
                rs.getLong("accepted_version"), rs.getInt("applied_value"), rs.getLong("applied_version"));
    }

    public void accept(long postId, long userId, int value, long version, int total) {
        if (state(postId, userId) == null) {
            jdbc.update("INSERT INTO vote_states(post_id,user_id,desired_value,accepted_version,applied_value,applied_version) "
                    + "VALUES(?,?,?,?,0,0)", postId, userId, value, version);
        } else {
            jdbc.update("UPDATE vote_states SET desired_value=?,accepted_version=? WHERE post_id=? AND user_id=?",
                    value, version, postId, userId);
        }
        jdbc.update("UPDATE post_vote_clocks SET accepted_version=?,accepted_total=?,updated_at=CURRENT_TIMESTAMP WHERE post_id=?",
                version, total, postId);
    }

    public void applied(long postId, long userId, int value, long version) {
        jdbc.update("UPDATE vote_states SET applied_value=?,applied_version=? WHERE post_id=? AND user_id=?", value, version, postId, userId);
    }

    public void repairAcceptedTotal(long postId, int total) {
        jdbc.update("UPDATE post_vote_clocks SET accepted_total=? WHERE post_id=?", total, postId);
    }

    public List<Long> activePosts() {
        return jdbc.queryForList("SELECT p.id FROM posts p LEFT JOIN post_vote_clocks c ON c.post_id=p.id "
                + "WHERE p.updated_at>=? OR c.updated_at>=? ORDER BY COALESCE(c.updated_at,p.updated_at) DESC,p.id DESC LIMIT 500", Long.class,
                java.time.LocalDateTime.now().minusHours(2), java.time.LocalDateTime.now().minusHours(2));
    }

    public List<Clock> clocks(List<Long> postIds) {
        if (postIds.isEmpty()) { return List.of(); }
        return jdbc.query("SELECT * FROM post_vote_clocks WHERE post_id IN ("
                        + String.join(",", java.util.Collections.nCopies(postIds.size(), "?")) + ")",
                (rs, row) -> new Clock(rs.getLong("post_id"), rs.getLong("accepted_version"), rs.getInt("accepted_total")), postIds.toArray());
    }

    public List<State> userStates(List<Long> postIds, long userId) {
        if (postIds.isEmpty()) { return List.of(); }
        java.util.ArrayList<Object> args = new java.util.ArrayList<>(postIds);
        args.add(userId);
        return jdbc.query("SELECT * FROM vote_states WHERE post_id IN ("
                + String.join(",", java.util.Collections.nCopies(postIds.size(), "?")) + ") AND user_id=?", this::mapState, args.toArray());
    }
}
