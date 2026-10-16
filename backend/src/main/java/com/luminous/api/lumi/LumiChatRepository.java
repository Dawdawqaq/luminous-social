package com.luminous.api.lumi;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.*;
import java.util.*;

/** 仅通过认证用户编号访问私有对话；时间统一使用显式 UTC，返回浏览器时附带 Z。 */
@Repository
public class LumiChatRepository {
    public record Turn(long id,long userId,long sessionId,String question,String answer,String status,String message,
                       LocalDateTime acceptedAt,LocalDateTime createdAt,String leaseToken,int attempts,long revision){}
    private final JdbcTemplate jdbc;
    public LumiChatRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public Long session(long user){return jdbc.queryForList("SELECT session_id FROM lumi_chat_sessions WHERE user_id=?",Long.class,user).stream().findFirst().orElse(null);}
    public void session(long user,long session,LocalDateTime now){jdbc.update("INSERT INTO lumi_chat_sessions(user_id,session_id,updated_at) VALUES(?,?,?) ON DUPLICATE KEY UPDATE session_id=VALUES(session_id),updated_at=VALUES(updated_at)",user,session,now);}
    public void removePointer(long user){jdbc.update("DELETE FROM lumi_chat_sessions WHERE user_id=?",user);}
    public boolean eligible(long user){return jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id=? AND is_banned=FALSE AND account_type='HUMAN'",Long.class,user)>0;}
    public void lockUser(long user){jdbc.queryForList("SELECT id FROM users WHERE id=? FOR SHARE",Long.class,user);}
    public Turn turn(long id){return query("SELECT * FROM lumi_chat_turns WHERE id=?",id).stream().findFirst().orElse(null);}
    public Turn lockTurn(long id){return query("SELECT * FROM lumi_chat_turns WHERE id=? FOR UPDATE",id).stream().findFirst().orElse(null);}
    public Turn ownedTurn(long user,long id){return query("SELECT t.* FROM lumi_chat_turns t JOIN lumi_chat_conversations s ON s.user_id=t.user_id AND s.id=t.session_id AND s.deleted_at IS NULL JOIN users u ON u.id=t.user_id WHERE t.id=? AND t.user_id=? AND u.is_banned=FALSE AND u.account_type='HUMAN'",id,user).stream().findFirst().orElse(null);}
    public Turn request(long user,String key){return query("SELECT * FROM lumi_chat_turns WHERE user_id=? AND request_key=?",user,key).stream().findFirst().orElse(null);}
    public Turn lockRequest(long user,String key){return query("SELECT * FROM lumi_chat_turns WHERE user_id=? AND request_key=? FOR UPDATE",user,key).stream().findFirst().orElse(null);}
    public List<Turn> page(long user,long session,Long before,int limit){return query("SELECT * FROM lumi_chat_turns WHERE user_id=? AND session_id=? "+(before==null?"":"AND id<"+before+" ")+"ORDER BY id DESC LIMIT "+limit,user,session);}
    public List<Turn> history(Turn turn,int limit){var rows=query("SELECT * FROM lumi_chat_turns WHERE user_id=? AND session_id=? AND id<? AND (status='SUCCEEDED' OR (status IN ('STOPPED','FAILED') AND answer IS NOT NULL AND answer<>'')) ORDER BY id DESC LIMIT "+limit,turn.userId(),turn.sessionId(),turn.id());Collections.reverse(rows);return rows;}
    public Turn active(long user){return query("SELECT * FROM lumi_chat_turns WHERE user_id=? AND status IN ('PENDING','RUNNING','STOPPING') ORDER BY id LIMIT 1",user).stream().findFirst().orElse(null);}
    public long activeInSession(long user,long session){return jdbc.queryForObject("SELECT COUNT(*) FROM lumi_chat_turns WHERE user_id=? AND session_id=? AND status IN ('PENDING','RUNNING','STOPPING')",Long.class,user,session);}
    public long daily(long user,LocalDate day){return jdbc.queryForObject("SELECT COUNT(*) FROM lumi_chat_turns WHERE user_id=? AND quota_day=? AND (status IN ('PENDING','RUNNING','STOPPING','SUCCEEDED') OR (status='STOPPED' AND attempts>0))",Long.class,user,day);}
    public LocalDateTime lastAccepted(long user){return jdbc.queryForObject("SELECT MAX(accepted_at) FROM lumi_chat_turns WHERE user_id=?",LocalDateTime.class,user);}
    /** 写入决策使用协调锁后的当前读，公开只读页面仍保留原快照查询。 */
    public long dailyCurrent(long user,LocalDate day){return jdbc.queryForList("SELECT id FROM lumi_chat_turns WHERE user_id=? AND quota_day=? AND (status IN ('PENDING','RUNNING','STOPPING','SUCCEEDED') OR (status='STOPPED' AND attempts>0)) FOR UPDATE",Long.class,user,day).size();}
    public LocalDateTime lastAcceptedCurrent(long user){return jdbc.queryForList("SELECT accepted_at FROM lumi_chat_turns WHERE user_id=? ORDER BY accepted_at DESC LIMIT 1 FOR UPDATE",LocalDateTime.class,user).stream().findFirst().orElse(null);}
    public boolean latest(Turn turn){return jdbc.queryForObject("SELECT COUNT(*) FROM lumi_chat_turns WHERE user_id=? AND session_id=? AND id>?",Long.class,turn.userId(),turn.sessionId(),turn.id())==0;}
    public boolean latestCurrent(Turn turn){return jdbc.queryForList("SELECT id FROM lumi_chat_turns WHERE user_id=? AND session_id=? AND id>? FOR UPDATE",Long.class,turn.userId(),turn.sessionId(),turn.id()).isEmpty();}
    public void insert(long id,long user,long session,String key,String question,LocalDateTime now){jdbc.update("INSERT INTO lumi_chat_turns(id,user_id,session_id,request_key,question,status,message,quota_day,accepted_at,created_at,updated_at) VALUES(?,?,?,?,?,'PENDING','Lumi 正在排队',?,?,?,?)",id,user,session,key,question,now.plusHours(8).toLocalDate(),now,now,now);}
    public void clear(long user,long session){jdbc.update("UPDATE lumi_chat_turns SET question='',answer=NULL,message=NULL,revision=revision+1 WHERE user_id=? AND session_id=?",user,session);}
    public boolean requeue(Turn t,LocalDateTime now){return jdbc.update("UPDATE lumi_chat_turns SET status='PENDING',answer=NULL,message='Lumi 正在排队',accepted_at=?,quota_day=?,updated_at=?,lease_token=NULL,lease_until=NULL,revision=revision+1 WHERE id=? AND status='FAILED' AND revision=? AND attempts=? AND lease_token <=> ?",now,now.plusHours(8).toLocalDate(),now,t.id(),t.revision(),t.attempts(),t.leaseToken())==1;}
    public Turn pending(){return query("SELECT * FROM lumi_chat_turns WHERE status='PENDING' ORDER BY created_at,id LIMIT 1").stream().findFirst().orElse(null);}
    public Turn lockPending(){return query("SELECT * FROM lumi_chat_turns WHERE status='PENDING' ORDER BY created_at,id LIMIT 1 FOR UPDATE").stream().findFirst().orElse(null);}
    public List<Turn> expired(LocalDateTime now){return query("SELECT * FROM lumi_chat_turns WHERE status IN ('RUNNING','STOPPING') AND lease_until<? LIMIT 20",now);}
    public List<Turn> lockExpired(LocalDateTime now){return query("SELECT * FROM lumi_chat_turns WHERE status IN ('RUNNING','STOPPING') AND lease_until<? ORDER BY id LIMIT 20 FOR UPDATE",now);}
    public void expirePending(LocalDateTime now){jdbc.update("UPDATE lumi_chat_turns SET status='FAILED',message='等待时间过长，请稍后重试',updated_at=?,revision=revision+1 WHERE status='PENDING' AND accepted_at<?",now,now.minusMinutes(10));}
    public void start(Turn turn,String token,LocalDateTime now,int seconds){jdbc.update("UPDATE lumi_chat_turns SET status='RUNNING',message='Lumi 正在思考',attempts=attempts+1,lease_token=?,lease_until=?,updated_at=?,revision=revision+1 WHERE id=?",token,now.plusSeconds(seconds),now,turn.id());}
    public void finish(long id,String state,String message,String answer,LocalDateTime now){jdbc.update("UPDATE lumi_chat_turns SET status=?,message=?,answer=?,lease_until=NULL,updated_at=?,revision=revision+1 WHERE id=?",state,message,answer,now,id);}
    public void stopping(long id,LocalDateTime now){jdbc.update("UPDATE lumi_chat_turns SET status='STOPPING',message='正在停止…',updated_at=?,revision=revision+1 WHERE id=? AND status='RUNNING'",now,id);}
    public boolean progress(long id,String token,String answer,LocalDateTime now){return jdbc.update("UPDATE lumi_chat_turns SET answer=?,message='Lumi 正在回答',updated_at=?,revision=revision+1 WHERE id=? AND status='RUNNING' AND lease_token=?",answer,now,id,token)==1;}
    public void checkpoint(long id,String token,String answer,LocalDateTime now){jdbc.update("UPDATE lumi_chat_turns SET answer=?,updated_at=?,revision=revision+1 WHERE id=? AND status IN ('RUNNING','STOPPING') AND lease_token=?",answer,now,id,token);}
    private List<Turn> query(String sql,Object... args){return jdbc.query(sql,(r,i)->new Turn(r.getLong("id"),r.getLong("user_id"),r.getLong("session_id"),r.getString("question"),r.getString("answer"),r.getString("status"),r.getString("message"),r.getObject("accepted_at",LocalDateTime.class),r.getObject("created_at",LocalDateTime.class),r.getString("lease_token"),r.getInt("attempts"),r.getLong("revision")),args);}
}
