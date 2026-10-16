package com.luminous.api.lumi;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.*;

/** 会话按账号归属及活动时间分页；删除只擦除正文和标题，保留额度与费用关联事实。 */
@Repository
public class LumiConversationRepository {
    public record Conversation(long id,long userId,String title,LocalDateTime activityAt,LocalDateTime deletedAt){}
    public record Cursor(LocalDateTime time,long id){}
    private final JdbcTemplate jdbc;
    public LumiConversationRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public Conversation owned(long user,long id){return query("SELECT * FROM lumi_chat_conversations WHERE user_id=? AND id=?",user,id).stream().findFirst().orElse(null);}
    public Conversation request(long user,String key){return query("SELECT * FROM lumi_chat_conversations WHERE user_id=? AND create_key=?",user,key).stream().findFirst().orElse(null);}
    public boolean exists(long user,long id){var row=owned(user,id);return row!=null&&row.deletedAt()==null;}
    public long count(long user){return jdbc.queryForObject("SELECT COUNT(*) FROM lumi_chat_conversations WHERE user_id=? AND deleted_at IS NULL",Long.class,user);}
    public List<Conversation> page(long user,Cursor cursor,int limit){
        String sql="SELECT * FROM lumi_chat_conversations WHERE user_id=? AND deleted_at IS NULL ";
        return cursor==null?query(sql+"ORDER BY activity_at DESC,id DESC LIMIT ?",user,limit)
                :query(sql+"AND (activity_at<? OR (activity_at=? AND id<?)) ORDER BY activity_at DESC,id DESC LIMIT ?",user,cursor.time(),cursor.time(),cursor.id(),limit);
    }
    public void insert(long id,long user,String key,LocalDateTime now){jdbc.update("INSERT INTO lumi_chat_conversations(id,user_id,title,create_key,created_at,activity_at) VALUES(?,?,'新对话',?,?,?)",id,user,key,now,now);}
    public void rename(long user,long id,String title){jdbc.update("UPDATE lumi_chat_conversations SET title=?,title_custom=TRUE WHERE id=? AND user_id=? AND deleted_at IS NULL",title,id,user);}
    public void touch(long user,long id,String firstTitle,LocalDateTime now){
        jdbc.update("UPDATE lumi_chat_conversations SET activity_at=?,title=CASE WHEN title_custom=FALSE AND NOT EXISTS (SELECT 1 FROM lumi_chat_turns WHERE user_id=? AND session_id=?) THEN ? ELSE title END WHERE id=? AND user_id=? AND deleted_at IS NULL",now,user,id,firstTitle,id,user);
    }
    public void activity(long user,long id,LocalDateTime now){jdbc.update("UPDATE lumi_chat_conversations SET activity_at=? WHERE id=? AND user_id=? AND deleted_at IS NULL",now,id,user);}
    public void delete(long user,long id,LocalDateTime now){jdbc.update("UPDATE lumi_chat_conversations SET title='已删除对话',deleted_at=? WHERE id=? AND user_id=? AND deleted_at IS NULL",now,id,user);}
    private List<Conversation> query(String sql,Object...args){return jdbc.query(sql,(r,i)->new Conversation(r.getLong("id"),r.getLong("user_id"),r.getString("title"),r.getObject("activity_at",LocalDateTime.class),r.getObject("deleted_at",LocalDateTime.class)),args);}
}
