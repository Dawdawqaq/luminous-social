package com.luminous.api.lumi;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.*;
import java.util.*;

/** 所有额度与任务状态变更由控制行串行协调，锁只覆盖短数据库事务。 */
@Repository
public class LumiRepository {
    public record Task(long id, long commentId, long postId, long userId, LocalDate day, String status,
                       LocalDateTime acceptedAt, String leaseToken, int attempts, Long replyId, String error, String message) {}
    public record Context(long rootId, String title, String content, String question, String parentText) {}
    public record Attempt(long id, long taskId, LocalDate day, long reserved) {}
    public record Request(long userId,long postId,String content) {}
    private final JdbcTemplate jdbc;
    private final LumiBudgetRepository budget;
    public LumiRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; this.budget=new LumiBudgetRepository(jdbc); }
    public Request request(long commentId) {
        return jdbc.query("SELECT author_id,post_id,content FROM comments WHERE id=?",(r,i)->new Request(r.getLong(1),r.getLong(2),r.getString(3)),commentId).stream().findFirst().orElse(null);
    }
    public Boolean readablePost(long post,long user) {
        return jdbc.query("SELECT p.is_public,p.status FROM posts p JOIN users u ON u.id=? WHERE p.id=? AND (p.is_public=TRUE OR p.author_id=? OR u.role='ADMIN') AND (p.status<>'BANNED' OR u.role='ADMIN')",
                (r,i)->r.getBoolean(1)&&List.of("NORMAL","PUBLISHED").contains(r.getString(2)),user,post,user).stream().findFirst().orElse(null);
    }
    public void expirePending(LocalDateTime now) {
        jdbc.update("UPDATE lumi_tasks SET status='CANCELLED',error_code='LUMI_QUEUE_TIMEOUT',message='等待时间过长，请稍后重新提问',updated_at=? WHERE status='PENDING' AND accepted_at<?",now,now.minusMinutes(10));
    }
    public boolean startAttempt(long id,long taskId) {return jdbc.update("UPDATE lumi_attempts SET status='STARTED' WHERE id=? AND task_id=? AND status='RESERVED'",id,taskId)==1;}
    public void insertReply(long reply,Task task,long root,String answer) {
        // 与普通评论的应用时间保持一致，避免数据库会话时区不同导致回复被显示成未来时间。
        var createdAt=LocalDateTime.now();
        jdbc.update("INSERT INTO comments(id,post_id,author_id,content,votes,parent_id,reply_to_comment_id,reply_to_user_id,created_at,updated_at) VALUES(?,?,?,?,0,?,?,?,?,?)",
                reply,task.postId(),LumiProperties.BOT_ID,answer,root,task.commentId(),task.userId(),createdAt,createdAt);
        jdbc.update("UPDATE posts SET comments_count=comments_count+1 WHERE id=?",task.postId());
    }
    public boolean visiblePost(long id) {return jdbc.queryForObject("SELECT COUNT(*) FROM posts WHERE id=? AND is_public=TRUE AND status IN ('NORMAL','PUBLISHED')",Long.class,id)>0;}
    public void coordinate() { jdbc.queryForObject("SELECT id FROM lumi_control WHERE id=1 FOR UPDATE", Integer.class); }
    public LocalDateTime now() { return jdbc.queryForObject("SELECT UTC_TIMESTAMP(3)", LocalDateTime.class); }
    public LocalDate day(LocalDateTime now) { return now.plusHours(8).toLocalDate(); }
    public Task task(long id) { return query("SELECT * FROM lumi_tasks WHERE id=?",id).stream().findFirst().orElse(null); }
    /** 先锁帖子及协调行，再当前读任务，不能把路由预读改成锁前任务锁。 */
    public Task lockTask(long id) { return query("SELECT * FROM lumi_tasks WHERE id=? FOR UPDATE",id).stream().findFirst().orElse(null); }
    public Task forComment(long id) { return query("SELECT * FROM lumi_tasks WHERE comment_id=?",id).stream().findFirst().orElse(null); }
    public Task lockForComment(long id) { return query("SELECT * FROM lumi_tasks WHERE comment_id=? FOR UPDATE",id).stream().findFirst().orElse(null); }
    public List<Task> own(long post, long user) { return query("SELECT * FROM lumi_tasks WHERE post_id=? AND user_id=? ORDER BY id DESC LIMIT 20",post,user); }
    public List<Task> expired(LocalDateTime now) { return query("SELECT * FROM lumi_tasks WHERE status='RUNNING' AND lease_until<? LIMIT 20",now); }
    public Task pending() { return query("SELECT * FROM lumi_tasks WHERE status='PENDING' ORDER BY created_at,id LIMIT 1").stream().findFirst().orElse(null); }
    public Task lockPending() { return query("SELECT * FROM lumi_tasks WHERE status='PENDING' ORDER BY created_at,id LIMIT 1 FOR UPDATE").stream().findFirst().orElse(null); }
    public List<Task> lockExpired(LocalDateTime now) { return query("SELECT * FROM lumi_tasks WHERE status='RUNNING' AND lease_until<? ORDER BY id LIMIT 20 FOR UPDATE",now); }
    private List<Task> query(String sql, Object... args) {
        return jdbc.query(sql,(r,i)->new Task(r.getLong("id"),r.getLong("comment_id"),r.getLong("post_id"),r.getLong("user_id"),r.getObject("quota_day",LocalDate.class),
                r.getString("status"),r.getObject("accepted_at",LocalDateTime.class),r.getString("lease_token"),r.getInt("attempts"),r.getObject("reply_id",Long.class),r.getString("error_code"),r.getString("message")),args);
    }
    public Long lockPost(long postId) { return jdbc.queryForList("SELECT id FROM posts WHERE id=? FOR UPDATE",Long.class,postId).stream().findFirst().orElse(null); }
    public void lockPeople(Task task) {
        for(long id: new java.util.TreeSet<>(List.of(task.userId(),LumiProperties.BOT_ID)))
            jdbc.queryForList("SELECT id FROM users WHERE id=? FOR SHARE",Long.class,id);
    }
    public Context context(long commentId) {
        return jdbc.query("""
            SELECT COALESCE(c.parent_id,c.id) root_id,p.title,p.content,c.content question,t.content parent_text
            FROM comments c JOIN posts p ON p.id=c.post_id
            JOIN comments root ON root.id=COALESCE(c.parent_id,c.id) AND root.post_id=p.id
            JOIN users u ON u.id=c.author_id
            JOIN users bot ON bot.id=? AND bot.account_type='BOT' AND bot.is_banned=FALSE
            LEFT JOIN comments t ON t.id=c.reply_to_comment_id AND t.post_id=p.id
            WHERE c.id=? AND u.is_banned=FALSE AND u.account_type='HUMAN'
              AND p.is_public=TRUE AND p.status IN ('NORMAL','PUBLISHED')
            """,(r,i)->new Context(r.getLong("root_id"),r.getString("title"),r.getString("content"),r.getString("question"),r.getString("parent_text")),LumiProperties.BOT_ID,commentId).stream().findFirst().orElse(null);
    }
    public List<String> discussion(long postId, Long rootId) {
        return jdbc.queryForList("SELECT content FROM comments WHERE post_id=? "
                +(rootId==null?"":"AND (id="+rootId+" OR parent_id="+rootId+") ")
                +"ORDER BY created_at DESC,id DESC LIMIT 21",String.class,postId);
    }
    public long count(String condition, Object... args) { return Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM lumi_tasks WHERE "+condition,Long.class,args)); }
    public long activeForUser(long user){return count("user_id=? AND status IN ('PENDING','RUNNING')",user)+jdbc.queryForObject("SELECT COUNT(*) FROM lumi_chat_turns WHERE user_id=? AND status IN ('PENDING','RUNNING','STOPPING')",Long.class,user);}
    public long allRunning(){return count("status='RUNNING'")+jdbc.queryForObject("SELECT COUNT(*) FROM lumi_chat_turns WHERE status IN ('RUNNING','STOPPING')",Long.class);}
    public long allPending(){return count("status='PENDING'")+jdbc.queryForObject("SELECT COUNT(*) FROM lumi_chat_turns WHERE status='PENDING'",Long.class);}
    /** 准入与执行决策在协调行之后读取当前记录，避免外层 RR 旧快照；展示查询仍使用快照读。 */
    public long countCurrent(String condition,Object... args){return currentRows("lumi_tasks",condition,args);}
    public long activeForUserCurrent(long user){return countCurrent("user_id=? AND status IN ('PENDING','RUNNING')",user)
            +currentRows("lumi_chat_turns","user_id=? AND status IN ('PENDING','RUNNING','STOPPING')",user);}
    public long allRunningCurrent(){return countCurrent("status='RUNNING'")+currentRows("lumi_chat_turns","status IN ('RUNNING','STOPPING')");}
    public long allPendingCurrent(){return countCurrent("status='PENDING'")+currentRows("lumi_chat_turns","status='PENDING'");}
    private long currentRows(String table,String condition,Object... args){
        return jdbc.queryForList("SELECT id FROM "+table+" WHERE "+condition+" FOR UPDATE",Long.class,args).size();
    }
    public LocalDateTime lastAcceptedCurrent(long user){return jdbc.queryForList("SELECT accepted_at FROM lumi_tasks WHERE user_id=? AND accepted_at IS NOT NULL ORDER BY accepted_at DESC LIMIT 1 FOR UPDATE",LocalDateTime.class,user).stream().findFirst().orElse(null);}
    public LocalDateTime lastAccepted(long user) { return jdbc.queryForObject("SELECT MAX(accepted_at) FROM lumi_tasks WHERE user_id=?",LocalDateTime.class,user); }
    public void insert(long id,long comment,long post,long user,LocalDateTime now,String state,String code,String message) {
        jdbc.update("INSERT INTO lumi_tasks(id,comment_id,post_id,user_id,quota_day,status,accepted_at,created_at,updated_at,error_code,message) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                id,comment,post,user,day(now),state,"PENDING".equals(state)?now:null,now,now,code,message);
    }
    public void state(long id,String status,String code,String message,Long reply) {
        jdbc.update("UPDATE lumi_tasks SET status=?,error_code=?,message=?,reply_id=?,lease_until=NULL,updated_at=UTC_TIMESTAMP(3) WHERE id=?",status,code,message,reply,id);
    }
    public boolean requeue(Task task,LocalDateTime now) {
        // 状态、尝试数和租约共同校验，陈旧调用不能覆盖成功回复或更新后的执行轮次。
        return jdbc.update("UPDATE lumi_tasks SET status='PENDING',quota_day=?,accepted_at=?,updated_at=?,lease_token=NULL,error_code=NULL,message='Lumi 正在排队' "
                +"WHERE id=? AND status='FAILED' AND attempts=? AND lease_token <=> ? AND reply_id IS NULL",
                day(now),now,now,task.id(),task.attempts(),task.leaseToken())==1;
    }
    public void start(Task task,String token,LocalDateTime now,int lease) {
        jdbc.update("UPDATE lumi_tasks SET status='RUNNING',lease_token=?,lease_until=?,message='Lumi 正在查看帖子',updated_at=? WHERE id=?",token,now.plusSeconds(lease),now,task.id());
    }
    public Attempt reserve(Task task,LumiProperties p,LocalDateTime now) {
        var attempt=budget.reserve(task.id(),task.attempts()+1,p,p.getMaxOutputTokens(),now);
        if(attempt==null)return null;
        jdbc.update("UPDATE lumi_tasks SET attempts=attempts+1 WHERE id=?",task.id());
        return attempt;
    }
    public void settle(Attempt a,Long actual,Integer input,Integer output) {
        budget.settle(a,actual,input,output);
    }
    public void settleUnknown(long taskId) {
        budget.settleUnknown(taskId);
    }
}
