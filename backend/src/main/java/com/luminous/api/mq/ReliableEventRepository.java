package com.luminous.api.mq;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

/** 持久事件及消费记录存储；消费记录必须与业务操作共用数据库事务。 */
@Repository
public class ReliableEventRepository {
    public record Event(String eventId, String eventType, Long postId, Long userId,
                        long businessVersion, String payload, String status) {}
    private final JdbcTemplate jdbc;
    public ReliableEventRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Event find(String eventId) {
        List<Event> events = jdbc.query("SELECT * FROM reliable_events WHERE event_id=?", this::map, eventId);
        return events.isEmpty() ? null : events.getFirst();
    }

    private Event map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new Event(rs.getString("event_id"), rs.getString("event_type"),
                rs.getObject("post_id", Long.class), rs.getObject("user_id", Long.class),
                rs.getLong("business_version"), rs.getString("payload"), rs.getString("status"));
    }

    public void insert(Event event) {
        jdbc.update("INSERT INTO reliable_events(event_id,event_type,post_id,user_id,business_version,payload,status) VALUES(?,?,?,?,?,?,?)",
                event.eventId(), event.eventType(), event.postId(), event.userId(), event.businessVersion(), event.payload(), event.status());
    }

    public boolean recordConsumption(String consumer, String eventId) {
        try {
            jdbc.update("INSERT INTO consumed_events(consumer_name,event_id) VALUES(?,?)", consumer, eventId);
            return true;
        } catch (DuplicateKeyException duplicate) {
            return false;
        }
    }

    public void consumed(String eventId) {
        jdbc.update("UPDATE reliable_events SET status='CONSUMED',last_error=NULL,updated_at=CURRENT_TIMESTAMP WHERE event_id=?", eventId);
        jdbc.update("UPDATE failed_messages SET status='REPLAYED',updated_at=CURRENT_TIMESTAMP WHERE event_id=? AND status='FAILED'", eventId);
    }

    public int attempts(String eventId) {
        Integer attempts = jdbc.queryForObject("SELECT attempts FROM reliable_events WHERE event_id=?", Integer.class, eventId);
        return attempts == null ? 0 : attempts;
    }

    public void sent(String eventId) {
        jdbc.update("UPDATE reliable_events SET status='SENT',attempts=attempts+1,next_attempt_at=?,updated_at=CURRENT_TIMESTAMP "
                + "WHERE event_id=? AND status IN ('PENDING','SENT')", LocalDateTime.now().plusSeconds(30), eventId);
    }

    public void retryLater(String eventId, String error) {
        jdbc.update("UPDATE reliable_events SET attempts=attempts+1,last_error=?,next_attempt_at=?,updated_at=CURRENT_TIMESTAMP "
                + "WHERE event_id=? AND status IN ('PENDING','SENT')", abbreviate(error), LocalDateTime.now().plusSeconds(10), eventId);
    }

    public void failed(String eventId) {
        jdbc.update("UPDATE reliable_events SET status='FAILED',updated_at=CURRENT_TIMESTAMP WHERE event_id=? AND status<>'CONSUMED'", eventId);
    }

    public List<Event> due() {
        return jdbc.query("SELECT * FROM reliable_events WHERE status IN ('PENDING','SENT') AND next_attempt_at<=? "
                + "ORDER BY next_attempt_at,event_id LIMIT 100", this::map, LocalDateTime.now());
    }

    public boolean hasVoteEvent(long postId, long userId, long version) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM reliable_events WHERE event_type='VOTE' AND post_id=? AND user_id=? "
                + "AND business_version=? AND status IN ('PENDING','SENT')", Long.class, postId, userId, version);
        return count != null && count > 0;
    }

    public static String abbreviate(String text) {
        if (text == null) { return "未知异常"; }
        return text.substring(0, Math.min(1000, text.length()));
    }
}
