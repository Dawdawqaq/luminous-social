package com.luminous.api.auth;

import com.luminous.api.common.SnowflakeIdGenerator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Arrays;
import java.util.stream.Collectors;

/** 关注操作额度及通知去重记录，必须由业务事务调用。 */
@Repository
public class FollowActionRepository {
    private final JdbcTemplate jdbc;
    public FollowActionRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Gate(LocalDateTime lastChangedAt,List<LocalDateTime> recentChanges) {}

    public Gate lockActor(long userId) {
        jdbc.update("INSERT INTO follow_action_gates(user_id) VALUES(?) ON DUPLICATE KEY UPDATE user_id=user_id",userId);
        return jdbc.queryForObject("SELECT last_changed_at,recent_change_times FROM follow_action_gates WHERE user_id=? FOR UPDATE",
                (rs,row) -> new Gate(rs.getObject(1,LocalDateTime.class),parseTimes(rs.getString(2))),userId);
    }

    public int changeRelation(long actorId,long targetId,boolean following) {
        return following
                ? jdbc.update("INSERT IGNORE INTO user_follows(follower_id,following_id) VALUES(?,?)",actorId,targetId)
                : jdbc.update("DELETE FROM user_follows WHERE follower_id=? AND following_id=?",actorId,targetId);
    }

    public LocalDateTime now() {
        return jdbc.queryForObject("SELECT CURRENT_TIMESTAMP(6)",(rs,row) -> rs.getObject(1,LocalDateTime.class));
    }

    private List<LocalDateTime> parseTimes(String value) {
        return value == null || value.isEmpty() ? List.of()
                : Arrays.stream(value.split(",")).map(LocalDateTime::parse).toList();
    }

    public void record(long userId,long targetId,boolean following,LocalDateTime now,List<LocalDateTime> recent) {
        jdbc.update("INSERT INTO follow_actions(id,user_id,target_id,is_following,changed_at) VALUES(?,?,?,?,?)",
                SnowflakeIdGenerator.getNextId(),userId,targetId,following,now);
        // 窗口最多三十个 ISO 时间戳，仅更新操作者的主键行，避免空区间的间隙锁。
        String times=java.util.stream.Stream.concat(recent.stream(),java.util.stream.Stream.of(now))
                .map(LocalDateTime::toString).collect(Collectors.joining(","));
        jdbc.update("UPDATE follow_action_gates SET last_changed_at=?,recent_change_times=? WHERE user_id=?",now,times,userId);
    }

    public boolean claimNotification(long actorId,long receiverId) {
        // 先创建占位行，再读取现有主键行，避免缺失行的间隙锁在不同用户之间形成死锁。
        jdbc.update("INSERT IGNORE INTO follow_notification_windows(actor_id,receiver_id) VALUES(?,?)",actorId,receiverId);
        LocalDateTime last = jdbc.queryForObject("SELECT last_event_at FROM follow_notification_windows WHERE actor_id=? AND receiver_id=? FOR UPDATE",
                (rs,row) -> rs.getObject(1,LocalDateTime.class),actorId,receiverId);
        LocalDateTime now = now();
        if(last != null && last.plusMinutes(10).isAfter(now)) return false;
        jdbc.update("UPDATE follow_notification_windows SET last_event_at=? WHERE actor_id=? AND receiver_id=?",now,actorId,receiverId);
        return true;
    }

    public int purgeExpired() {
        int actions = jdbc.update("DELETE FROM follow_actions WHERE changed_at<=CURRENT_TIMESTAMP(6)-INTERVAL 60 SECOND LIMIT 1000");
        int windows = jdbc.update("DELETE FROM follow_notification_windows WHERE last_event_at<=CURRENT_TIMESTAMP(6)-INTERVAL 10 MINUTE LIMIT 1000");
        return actions + windows;
    }
}
