package com.luminous.api.notification;

import com.luminous.api.common.SnowflakeIdGenerator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.*;

/** 通知写入与游标查询；批次按对象排序，仅竞争具体聚合行。 */
@Repository
public class NotificationInboxRepository {
    private final JdbcTemplate jdbc;
    public NotificationInboxRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    public void insertBatch(List<NotificationEvent> events) {
        Map<String,List<NotificationEvent>> groups=new TreeMap<>();
        for (NotificationEvent e : events) {
            if (Objects.equals(e.actorId(),e.receiverId())) continue;
            if (isLike(e.type())) groups.computeIfAbsent(key(e), ignored -> new ArrayList<>()).add(e);
            else insert(e,1,null);
        }
        for (var entry : groups.entrySet()) {
            List<NotificationEvent> accepted=new ArrayList<>();
            for (NotificationEvent e : entry.getValue().stream().sorted(Comparator.comparing(NotificationEvent::actorId)).toList()) {
                int inserted=jdbc.update("INSERT IGNORE INTO notification_like_receipts(receiver_id,type,target_id,actor_id,bucket_date) VALUES(?,?,?,?,?)",
                        e.receiverId(),e.type(),e.targetId(),e.actorId(),time(e).toLocalDate());
                if (inserted==1) accepted.add(e);
            }
            if (!accepted.isEmpty()) {
                NotificationEvent latest=accepted.stream().max(Comparator.comparing(NotificationInboxRepository::time)).orElseThrow();
                insert(latest,accepted.size(),entry.getKey());
            }
        }
    }

    private void insert(NotificationEvent e, int count, String key) {
        // 更新聚合人数时保留已读时间和水位，新版本恢复未读；软删除的旧行不复活。
        jdbc.update("""
            INSERT INTO notifications(id,receiver_id,actor_id,type,target_type,target_id,content_preview,is_read,
                created_at,updated_at,interaction_count,aggregate_key,post_id,comment_id)
            VALUES(?,?,?,?,?,?,?,false,?,?,?,?,?,?)
            ON DUPLICATE KEY UPDATE actor_id=VALUES(actor_id),interaction_count=interaction_count+VALUES(interaction_count),
                updated_at=GREATEST(updated_at,VALUES(updated_at)),is_read=false
            """, SnowflakeIdGenerator.getNextId(),e.receiverId(),e.actorId(),e.type(),e.targetType(),e.targetId(),
                e.contentPreview(),time(e),LocalDateTime.now(),count,key,e.postId(),e.commentId());
    }
    public static boolean isLike(String type) { return "LIKE_POST".equals(type)||"LIKE_COMMENT".equals(type); }
    private static LocalDateTime time(NotificationEvent e) { return e.occurredAt()==null ? LocalDateTime.now() : e.occurredAt(); }
    private static String key(NotificationEvent e) { return e.receiverId()+":"+e.type()+":"+e.targetId()+":"+time(e).toLocalDate(); }

    public List<Notification> slice(long receiver, String type, LocalDateTime beforeTime, long before, int limit) {
        List<Object> args=new ArrayList<>(List.of(receiver,beforeTime,beforeTime,before));
        String filter=filter(type,args);
        args.add(limit);
        return jdbc.query("SELECT * FROM notifications WHERE receiver_id=? AND deleted_at IS NULL AND (updated_at<? OR (updated_at=? AND id<?))"+filter+" ORDER BY updated_at DESC,id DESC LIMIT ?",
                (rs,row) -> {
                    Notification n=new Notification(); n.setId(rs.getLong("id")); n.setReceiverId(rs.getLong("receiver_id"));
                    n.setActorId(rs.getLong("actor_id")); n.setType(rs.getString("type")); n.setTargetType(rs.getString("target_type"));
                    n.setTargetId(rs.getLong("target_id")); n.setContentPreview(rs.getString("content_preview")); n.setIsRead(rs.getBoolean("is_read"));
                    n.setCreatedAt(rs.getObject("created_at",LocalDateTime.class)); n.setUpdatedAt(rs.getObject("updated_at",LocalDateTime.class));
                    n.setInteractionCount(rs.getLong("interaction_count")); n.setPostId(rs.getObject("post_id",Long.class));
                    n.setCommentId(rs.getObject("comment_id",Long.class)); return n;
                },args.toArray());
    }
    private String filter(String type,List<Object> args) {
        List<String> types=switch(type) {
            case "LIKE" -> List.of("LIKE_POST","LIKE_COMMENT"); case "COMMENT" -> List.of("COMMENT_POST","REPLY_COMMENT");
            case "FOLLOW" -> List.of("FOLLOW_USER"); case "SYSTEM" -> List.of("SYSTEM"); default -> List.of();
        };
        if(types.isEmpty()) return "";
        args.addAll(types); return " AND type IN ("+String.join(",",Collections.nCopies(types.size(),"?"))+")";
    }
    public Map<String,Long> unread(long receiver) {
        Map<String,Long> counts=new HashMap<>(); counts.put("ALL",0L);
        jdbc.query("SELECT type,COUNT(*) AS total FROM notifications WHERE receiver_id=? AND deleted_at IS NULL AND is_read=false GROUP BY type",
                rs -> { String type=rs.getString("type"); long count=rs.getLong("total");
                    String group=isLike(type)?"LIKE":("COMMENT_POST".equals(type)||"REPLY_COMMENT".equals(type))?"COMMENT":"FOLLOW_USER".equals(type)?"FOLLOW":"SYSTEM";
                    counts.merge(group,count,Long::sum); counts.merge("ALL",count,Long::sum);
                },receiver);
        return counts;
    }
    public void read(long id,long receiver,Long seenVersion) {
        // 单条阅读只推进到客户端看到的版本，SQL 同一行锁内判断是否仍有未读更新。
        long version=seenVersion==null?Long.MAX_VALUE:seenVersion;
        jdbc.update("UPDATE notifications SET is_read=(GREATEST(read_version,LEAST(interaction_count,?))>=interaction_count),"
                +"read_version=GREATEST(read_version,LEAST(interaction_count,?)),read_at=? "
                +"WHERE id=? AND receiver_id=? AND deleted_at IS NULL",version,version,LocalDateTime.now(),id,receiver);
    }
    public String revision(long receiver) {
        List<String> rows=jdbc.query("SELECT id,updated_at FROM notifications WHERE receiver_id=? AND deleted_at IS NULL ORDER BY updated_at DESC,id DESC LIMIT 1",
                (rs,row) -> rs.getLong("id")+":"+rs.getObject("updated_at",LocalDateTime.class),receiver);
        return rows.isEmpty()?"":rows.getFirst();
    }
    public int readAll(long receiver) {
        // 一条更新语句以执行时的当前行版本为准，随后提交的新互动会重新设为未读。
        return jdbc.update("UPDATE notifications SET read_version=interaction_count,read_at=?,is_read=true "
                +"WHERE receiver_id=? AND deleted_at IS NULL AND is_read=false",LocalDateTime.now(),receiver);
    }
    public void delete(long id,long receiver) {
        jdbc.update("UPDATE notifications SET deleted_at=?,aggregate_key=NULL WHERE id=? AND receiver_id=? AND deleted_at IS NULL",LocalDateTime.now(),id,receiver);
    }
    public int purge(int days) {
        LocalDateTime cutoff=LocalDateTime.now().minusDays(days);
        int deleted=jdbc.update("DELETE FROM notifications WHERE updated_at<? AND type IN ('LIKE_POST','LIKE_COMMENT','COMMENT_POST','REPLY_COMMENT','FOLLOW_USER') LIMIT 1000",cutoff);
        int receipts=jdbc.update("DELETE FROM notification_like_receipts WHERE bucket_date<? LIMIT 1000",cutoff.toLocalDate());
        return deleted+receipts;
    }
}
