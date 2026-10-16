package com.luminous.api.growth;

import com.luminous.api.common.SnowflakeIdGenerator;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 所有写入由业务事务包围，个人余额行是同一用户全站奖励及限额的串行化边界。 */
@Repository
public class GrowthRepository {
    public record AccountKey(long userId, long communityId) {}
    public record Content(String type, long id, long userId, long communityId, long postId,
                          boolean eligible, String text) {}
    public record Reward(long id, String sourceKey, long userId, long communityId, String action,
                         String contentType, long contentId, long amount, String state, long revision) {}
    public record Session(String token, long postId, long readerId, Instant startedAt, Instant expiresAt, boolean completed) {}
    private final JdbcTemplate jdbc;
    public GrowthRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Content lockContent(String type, long id) {
        if ("POST".equals(type)) return lockPost(id);
        if (!"COMMENT".equals(type)) throw new IllegalArgumentException("成长内容类型无效");
        List<Long> postIds = jdbc.queryForList("SELECT post_id FROM comments WHERE id=?", Long.class, id);
        if (postIds.isEmpty()) return null;
        Content parent = lockPost(postIds.getFirst());
        if (parent == null) return null;
        return jdbc.query("SELECT author_id,content FROM comments WHERE id=? FOR UPDATE", (rs, row) ->
                new Content(type, id, rs.getLong(1), parent.communityId(), parent.id(), parent.eligible(), rs.getString(2)), id)
                .stream().findFirst().orElse(null);
    }

    public Content lockPost(long id) {
        return jdbc.query("SELECT author_id,community_id,is_public,status,title,content FROM posts WHERE id=? FOR UPDATE",
                (rs, row) -> new Content("POST", id, rs.getLong(1), rs.getLong(2), id,
                        rs.getBoolean(3) && ("NORMAL".equals(rs.getString(4)) || "PUBLISHED".equals(rs.getString(4))),
                        rs.getString(5) + "\n" + (rs.getString(6) == null ? "" : rs.getString(6))), id)
                .stream().findFirst().orElse(null);
    }

    /** 内容已删除时锁定留存主体，作者和社区归属来自历史快照，不接受客户端指定。 */
    public Content lockRetainedContent(String type, long id) {
        if (!"POST".equals(type) && !"COMMENT".equals(type)) throw new IllegalArgumentException("成长内容类型无效");
        return jdbc.query("SELECT user_id,community_id FROM growth_subjects WHERE content_type=? AND content_id=? FOR UPDATE",
                (rs, row) -> new Content(type, id, rs.getLong(1), rs.getLong(2), "POST".equals(type) ? id : 0,
                        false, ""), type, id).stream().findFirst().orElse(null);
    }

    public void initialize(long userId, long communityId) {
        jdbc.update("INSERT INTO growth_accounts(user_id,community_id) VALUES(?,?) "
                + "ON DUPLICATE KEY UPDATE user_id=VALUES(user_id)", userId, communityId);
    }

    /** 在成长账户之前按用户编号锁定参与者，资格共享锁由外层业务事务持有到提交。 */
    public void lockUsers(long... userIds) {
        for (long userId : Arrays.stream(userIds).distinct().sorted().toArray()) {
            jdbc.queryForList("SELECT id FROM users WHERE id=? FOR SHARE", Long.class, userId);
        }
    }

    public void lockAccounts(long userId, long communityId) {
        // 先持有作者共享锁，避免额度行锁与签到的用户排他锁通过外键检查形成环路。
        jdbc.queryForObject("SELECT id FROM users WHERE id=? FOR SHARE", Long.class, userId);
        initialize(userId, 0);
        jdbc.queryForObject("SELECT experience FROM growth_accounts WHERE user_id=? AND community_id=0 FOR UPDATE", Long.class, userId);
        initialize(userId, communityId);
        jdbc.queryForObject("SELECT experience FROM growth_accounts WHERE user_id=? AND community_id=? FOR UPDATE", Long.class, userId, communityId);
    }

    public void changeBalances(long userId, long communityId, long delta) {
        for (long scope : new long[]{0, communityId}) {
            long balance = jdbc.queryForObject("SELECT experience FROM growth_accounts WHERE user_id=? AND community_id=? FOR UPDATE",
                    Long.class, userId, scope);
            long updated = Math.addExact(balance, delta);
            if (updated < 0 || jdbc.update("UPDATE growth_accounts SET experience=? WHERE user_id=? AND community_id=?",
                    updated, userId, scope) != 1) throw new IllegalStateException("成长余额与奖励流水不一致");
        }
    }

    public Long balance(long userId, long communityId) {
        return jdbc.queryForList("SELECT experience FROM growth_accounts WHERE user_id=? AND community_id=?", Long.class,
                userId, communityId).stream().findFirst().orElse(null);
    }

    public Map<Long, Long> balances(Collection<Long> userIds, long communityId) {
        if (userIds.isEmpty()) return Map.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(userIds.size(), "?"));
        var arguments = new java.util.ArrayList<Object>();
        arguments.add(communityId); arguments.addAll(userIds);
        Map<Long, Long> result = new HashMap<>();
        jdbc.query("SELECT user_id,experience FROM growth_accounts WHERE community_id=? AND user_id IN (" + placeholders + ")",
                rs -> { result.put(rs.getLong(1), rs.getLong(2)); }, arguments.toArray());
        return result;
    }

    public boolean activeUser(long userId) {
        // 资格采用当前读；不能让外层 RR 旧快照恢复封禁账号的奖励。
        return jdbc.queryForList("SELECT (NOT is_banned AND account_type='HUMAN') FROM users WHERE id=? FOR SHARE", Boolean.class, userId).stream().anyMatch(Boolean.TRUE::equals);
    }

    public Map<AccountKey, Long> balances(Collection<Long> userIds, Collection<Long> communityIds) {
        if (userIds.isEmpty() || communityIds.isEmpty()) return Map.of();
        String users = String.join(",", java.util.Collections.nCopies(userIds.size(), "?"));
        String communities = String.join(",", java.util.Collections.nCopies(communityIds.size(), "?"));
        var arguments = new java.util.ArrayList<Object>();
        arguments.addAll(userIds); arguments.addAll(communityIds);
        Map<AccountKey, Long> result = new HashMap<>();
        jdbc.query("SELECT user_id,community_id,experience FROM growth_accounts WHERE user_id IN (" + users
                        + ") AND community_id IN (" + communities + ")",
                rs -> { result.put(new AccountKey(rs.getLong(1), rs.getLong(2)), rs.getLong(3)); }, arguments.toArray());
        return result;
    }

    public boolean prepareSubject(Content content) {
        jdbc.update("INSERT INTO growth_subjects(content_type,content_id,user_id,community_id) VALUES(?,?,?,?) "
                + "ON DUPLICATE KEY UPDATE content_id=VALUES(content_id)", content.type(), content.id(), content.userId(), content.communityId());
        return !Boolean.TRUE.equals(jdbc.queryForObject("SELECT blocked FROM growth_subjects WHERE content_type=? AND content_id=? FOR UPDATE",
                Boolean.class, content.type(), content.id()));
    }

    /** 在来源已通过唯一键建立后执行当前读，调用者不可用缺行锁定查询进行初始化。 */
    public Reward reward(String source) {
        return jdbc.query("SELECT * FROM growth_rewards WHERE source_key=? FOR UPDATE", (rs, row) -> rewardRow(rs), source)
                .stream().findFirst().orElse(null);
    }

    public boolean rewardExists(String source) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM growth_rewards WHERE source_key=?)", Boolean.class, source));
    }

    private Reward rewardRow(ResultSet rs) throws SQLException {
        return new Reward(rs.getLong("id"), rs.getString("source_key"), rs.getLong("user_id"), rs.getLong("community_id"),
                rs.getString("action"), rs.getString("content_type"), rs.getLong("content_id"), rs.getLong("amount"),
                rs.getString("state"), rs.getLong("revision"));
    }

    public long reserve(long userId, LocalDate date, String action, long requested, long cap) {
        jdbc.update("INSERT INTO growth_daily_usage(user_id,reward_date,action) VALUES(?,?,?) "
                + "ON DUPLICATE KEY UPDATE user_id=VALUES(user_id)", userId, date, action);
        long used = jdbc.queryForObject("SELECT used_experience FROM growth_daily_usage WHERE user_id=? AND reward_date=? AND action=? FOR UPDATE",
                Long.class, userId, date, action);
        long amount = Math.min(requested, Math.max(0, cap - used));
        jdbc.update("UPDATE growth_daily_usage SET used_experience=? WHERE user_id=? AND reward_date=? AND action=?",
                Math.addExact(used, amount), userId, date, action);
        return amount;
    }

    /** 唯一键先建立事务内待决策来源，不覆盖已提交的奖励；待决策状态不得独立提交。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public Reward initializeReward(String source, Content content, Long actorId, String action, LocalDate date,
                                   String ruleVersion) {
        long id = SnowflakeIdGenerator.getNextId();
        jdbc.update("INSERT INTO growth_rewards(id,source_key,user_id,community_id,action,content_type,content_id,actor_id,"
                        + "reward_date,amount,state,reason,rule_version,parent_post_id) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?) "
                        + "ON DUPLICATE KEY UPDATE id=id",
                id, source, content.userId(), content.communityId(), action, content.type(), content.id(), actorId,
                date, 0, "PENDING", "等待事务内奖励决策", ruleVersion, content.postId());
        Reward reward = reward(source);
        if (reward == null) throw new IllegalStateException("成长奖励来源初始化失败");
        return reward;
    }

    /** 在原事务内形成最终决定并追加首笔流水，额度不足也保留零奖励决定。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void completeReward(Reward pending, long amount, String reason) {
        String state = amount > 0 ? "ACTIVE" : "SUPPRESSED";
        if (jdbc.update("UPDATE growth_rewards SET amount=?,state=?,reason=? WHERE id=? AND state='PENDING' AND revision=1",
                amount, state, reason, pending.id()) != 1) throw new IllegalStateException("成长奖励决策状态更新失败");
        ledger(new Reward(pending.id(), pending.sourceKey(), pending.userId(), pending.communityId(), pending.action(),
                pending.contentType(), pending.contentId(), amount, state, 1), amount, "GRANT", reason);
    }

    /** 无有效点赞或资格不足时移除本事务刚建立的来源，不影响已有奖励及未来合法点赞。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void discardPendingReward(Reward reward) {
        if ("PENDING".equals(reward.state()) && jdbc.update(
                "DELETE FROM growth_rewards WHERE id=? AND state='PENDING' AND revision=1", reward.id()) != 1) {
            throw new IllegalStateException("成长待决策来源清理失败");
        }
    }

    public void transition(Reward reward, String state, long delta, String operation, String reason) {
        long revision = Math.addExact(reward.revision(), 1);
        if (jdbc.update("UPDATE growth_rewards SET state=?,revision=?,reason=? WHERE id=? AND revision=?",
                state, revision, reason, reward.id(), reward.revision()) != 1) throw new IllegalStateException("成长奖励版本更新失败");
        ledger(new Reward(reward.id(), reward.sourceKey(), reward.userId(), reward.communityId(), reward.action(),
                reward.contentType(), reward.contentId(), reward.amount(), state, revision), delta, operation, reason);
    }

    private void ledger(Reward reward, long delta, String operation, String reason) {
        jdbc.update("INSERT INTO growth_ledger(id,reward_id,revision,delta,operation,reason) VALUES(?,?,?,?,?,?)",
                SnowflakeIdGenerator.getNextId(), reward.id(), reward.revision(), delta, operation, reason);
    }

    public boolean fingerprint(long userId, String action, String fingerprint, long contentId) {
        try {
            jdbc.update("INSERT INTO growth_content_fingerprints(user_id,action,fingerprint,content_id) VALUES(?,?,?,?)",
                    userId, action, fingerprint, contentId);
            return true;
        } catch (DuplicateKeyException duplicate) { return false; }
    }

    public long commentsAwarded(long userId, long postId, LocalDate date) {
        return jdbc.queryForList("SELECT r.id FROM growth_rewards r "
                + "WHERE r.user_id=? AND r.action='COMMENT' AND r.content_type='COMMENT' AND r.parent_post_id=? "
                + "AND r.reward_date=? AND r.amount>0 FOR UPDATE", Long.class, userId, postId, date).size();
    }

    public int currentLike(Content content, long actorId) {
        String sql = "POST".equals(content.type())
                ? "SELECT desired_value FROM vote_states WHERE post_id=? AND user_id=? FOR UPDATE"
                : "SELECT value FROM comment_votes WHERE comment_id=? AND user_id=? FOR UPDATE";
        return jdbc.queryForList(sql, Integer.class, content.id(), actorId).stream().findFirst().orElse(0);
    }

    public void block(Content content, String reason) {
        prepareSubject(content);
        jdbc.update("UPDATE growth_subjects SET blocked=TRUE,reason=? WHERE content_type=? AND content_id=?", reason, content.type(), content.id());
    }

    public List<Reward> contentRewards(Content content) {
        return jdbc.query("SELECT * FROM growth_rewards WHERE content_type=? AND content_id=? ORDER BY id FOR UPDATE",
                (rs, row) -> rewardRow(rs), content.type(), content.id());
    }

    public void banPost(long postId) { jdbc.update("UPDATE posts SET status='BANNED' WHERE id=?", postId); }

    public void insertSession(Session session) {
        jdbc.update("INSERT INTO growth_read_sessions(token,post_id,reader_id,started_at,expires_at) VALUES(?,?,?,?,?)",
                session.token(), session.postId(), session.readerId(), utc(session.startedAt()), utc(session.expiresAt()));
    }

    public Session session(String token, boolean lock) {
        return jdbc.query("SELECT * FROM growth_read_sessions WHERE token=?" + (lock ? " FOR UPDATE" : ""), (rs, row) ->
                new Session(rs.getString("token"), rs.getLong("post_id"), rs.getLong("reader_id"),
                        rs.getObject("started_at", LocalDateTime.class).toInstant(ZoneOffset.UTC),
                        rs.getObject("expires_at", LocalDateTime.class).toInstant(ZoneOffset.UTC), rs.getBoolean("completed")), token)
                .stream().findFirst().orElse(null);
    }

    public boolean qualify(long postId, long readerId, Instant instant) {
        try {
            jdbc.update("INSERT INTO growth_qualified_reads(post_id,reader_id,qualified_at) VALUES(?,?,?)", postId, readerId, utc(instant));
            return true;
        } catch (DuplicateKeyException duplicate) { return false; }
    }

    public long incrementReads(long postId) {
        jdbc.update("UPDATE growth_subjects SET qualified_reads=qualified_reads+1 WHERE content_type='POST' AND content_id=?", postId);
        return jdbc.queryForObject("SELECT qualified_reads FROM growth_subjects WHERE content_type='POST' AND content_id=? FOR UPDATE", Long.class, postId);
    }

    public void completeSession(String token) { jdbc.update("UPDATE growth_read_sessions SET completed=TRUE WHERE token=?", token); }
    public void removeExpiredSessions(Instant now) { jdbc.update("DELETE FROM growth_read_sessions WHERE expires_at<?", utc(now)); }
    private static LocalDateTime utc(Instant value) { return LocalDateTime.ofInstant(value, ZoneOffset.UTC); }

    public List<GrowthDtos.LedgerEntry> ledger(long userId, long page, long size) {
        return jdbc.query("SELECT l.*,r.source_key,r.community_id,r.action,r.content_type,r.content_id,r.reward_date,r.rule_version "
                        + "FROM growth_ledger l JOIN growth_rewards r ON r.id=l.reward_id WHERE r.user_id=? ORDER BY l.id DESC LIMIT ? OFFSET ?",
                (rs, row) -> new GrowthDtos.LedgerEntry(String.valueOf(rs.getLong("id")), String.valueOf(rs.getLong("reward_id")),
                        rs.getString("source_key"), String.valueOf(rs.getLong("community_id")), rs.getString("action"),
                        rs.getString("content_type"), String.valueOf(rs.getLong("content_id")), rs.getDate("reward_date").toLocalDate(),
                        rs.getLong("delta"), rs.getString("operation"), rs.getString("reason"), rs.getString("rule_version"),
                        rs.getTimestamp("created_at").toInstant()), userId, size, Math.multiplyExact(page - 1, size));
    }

    public long ledgerCount(long userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM growth_ledger l JOIN growth_rewards r ON r.id=l.reward_id WHERE r.user_id=?", Long.class, userId);
    }
}
