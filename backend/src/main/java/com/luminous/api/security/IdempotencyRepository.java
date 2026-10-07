package com.luminous.api.security;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

/** 插入竞争等待原事务完成，锁定读取已提交结果；不能把业务提交与结果提交拆开。 */
@Repository
public class IdempotencyRepository {
    public record Result(String fingerprint, String status, int responseStatus, String contentType, String body, LocalDateTime expiresAt) {}
    private final JdbcTemplate jdbc;
    public IdempotencyRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Result claim(String scope, String key, String fingerprint, LocalDateTime expiry) {
        try { insert(scope, key, fingerprint, expiry); return null; }
        catch (DuplicateKeyException duplicate) {
            List<Result> results = jdbc.query("SELECT * FROM idempotent_requests WHERE scope_hash=? AND operation_key=? FOR UPDATE",
                    (rs, row) -> new Result(rs.getString("fingerprint"), rs.getString("status"), rs.getInt("response_status"),
                            rs.getString("content_type"), rs.getString("response_body"), rs.getTimestamp("expires_at").toLocalDateTime()), scope, key);
            if (results.isEmpty()) { throw new IllegalStateException("幂等记录并发变化，请重试"); }
            Result result = results.getFirst();
            if ("COMPLETED".equals(result.status()) && result.expiresAt().isBefore(LocalDateTime.now())) {
                jdbc.update("DELETE FROM idempotent_requests WHERE scope_hash=? AND operation_key=?", scope, key);
                insert(scope, key, fingerprint, expiry); return null;
            }
            return result;
        }
    }
    private void insert(String scope, String key, String fingerprint, LocalDateTime expiry) {
        jdbc.update("INSERT INTO idempotent_requests(scope_hash,operation_key,fingerprint,status,expires_at) VALUES(?,?,?,'PROCESSING',?)",
                scope, key, fingerprint, expiry);
    }
    public void complete(String scope, String key, int status, String type, String body) {
        jdbc.update("UPDATE idempotent_requests SET status='COMPLETED',response_status=?,content_type=?,response_body=? "
                + "WHERE scope_hash=? AND operation_key=?", status, type, body, scope, key);
    }
    public void purgeExpired() { jdbc.update("DELETE FROM idempotent_requests WHERE status='COMPLETED' AND expires_at<CURRENT_TIMESTAMP"); }
}
