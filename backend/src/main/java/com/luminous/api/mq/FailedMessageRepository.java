package com.luminous.api.mq;

import com.luminous.api.common.SnowflakeIdGenerator;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;

/** 失败消息保留原始事件身份和载荷，供管理员查询及重放。 */
@Repository
public class FailedMessageRepository {
    public record Failure(String id, String consumer, String eventId, String payload, int attempts, String lastError, String status) {}
    private final JdbcTemplate jdbc;
    public FailedMessageRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public void save(String consumer, String eventId, String payload, int attempts, String error) {
        String safeError = ReliableEventRepository.abbreviate(error);
        int updated = jdbc.update("UPDATE failed_messages SET payload=?,attempts=GREATEST(attempts,?),last_error=?,"
                + "status='FAILED',updated_at=CURRENT_TIMESTAMP WHERE consumer_name=? AND event_id=?",
                payload, attempts, safeError, consumer, eventId);
        if (updated == 0) {
            try {
                jdbc.update("INSERT INTO failed_messages(id,consumer_name,event_id,payload,attempts,last_error) VALUES(?,?,?,?,?,?)",
                        SnowflakeIdGenerator.getNextId(), consumer, eventId, payload, attempts, safeError);
            } catch (DuplicateKeyException duplicate) {
                jdbc.update("UPDATE failed_messages SET attempts=GREATEST(attempts,?),last_error=?,status='FAILED',"
                        + "updated_at=CURRENT_TIMESTAMP WHERE consumer_name=? AND event_id=?", attempts, safeError, consumer, eventId);
            }
        }
    }

    private Failure map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new Failure(rs.getString("id"), rs.getString("consumer_name"), rs.getString("event_id"),
                rs.getString("payload"), rs.getInt("attempts"), rs.getString("last_error"), rs.getString("status"));
    }
    public List<Failure> list(int offset, int size) {
        return jdbc.query("SELECT * FROM failed_messages WHERE status='FAILED' ORDER BY updated_at DESC,id DESC LIMIT ? OFFSET ?", this::map, size, offset);
    }
    public Failure find(long id) {
        List<Failure> results = jdbc.query("SELECT * FROM failed_messages WHERE id=?", this::map, id);
        return results.isEmpty() ? null : results.getFirst();
    }
}
