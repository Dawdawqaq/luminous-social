package com.luminous.api.post;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

/** 用户级锁与成功记录共用评论事务，使用锁定读取避免旧快照漏掉刚提交的发布。 */
@Repository
public class CommentPublicationRepository {
    private final JdbcTemplate jdbc;
    public CommentPublicationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public LocalDateTime lockUser(long userId) {
        jdbc.update("INSERT INTO comment_publish_gates(user_id) VALUES(?) ON DUPLICATE KEY UPDATE user_id=user_id", userId);
        return jdbc.queryForObject("SELECT last_published_at FROM comment_publish_gates WHERE user_id=? FOR UPDATE",
                (rs, row) -> rs.getObject(1, LocalDateTime.class), userId);
    }

    public LocalDateTime now() {
        return jdbc.queryForObject("SELECT CURRENT_TIMESTAMP(6)", (rs, row) -> rs.getObject(1, LocalDateTime.class));
    }

    public List<LocalDateTime> recent(long userId, LocalDateTime cutoff) {
        jdbc.update("DELETE FROM comment_publications WHERE user_id=? AND published_at<=?", userId, cutoff);
        return jdbc.query("SELECT published_at FROM comment_publications WHERE user_id=? AND published_at>? ORDER BY published_at FOR UPDATE",
                (rs, row) -> rs.getObject(1, LocalDateTime.class), userId, cutoff);
    }

    public void record(long userId, long commentId) {
        LocalDateTime time = now();
        jdbc.update("INSERT INTO comment_publications(comment_id,user_id,published_at) VALUES(?,?,?)", commentId, userId, time);
        jdbc.update("UPDATE comment_publish_gates SET last_published_at=? WHERE user_id=?", time, userId);
    }
}
