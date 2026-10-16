package com.luminous.api.lumi;

import com.luminous.api.common.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

/** 短事务完成私有对话准入、共享预算和发布，外部 HTTP 由工作器执行。 */
@Service
public class LumiChatService {
    private final LumiChatRepository chat;
    private final LumiRepository common;
    private final LumiBudgetRepository budget;
    private final LumiProperties p;
    private final LumiChatProperties cp;
    private final LumiConversationRepository conversations;
    public LumiChatService(LumiChatRepository chat,LumiRepository common,LumiBudgetRepository budget,LumiProperties p,LumiChatProperties cp,LumiConversationRepository conversations){this.chat=chat;this.common=common;this.budget=budget;this.p=p;this.cp=cp;this.conversations=conversations;cp.validate();}
    @Transactional
    public LumiChatDtos.Page read(long user,Long before){
        common.coordinate();requireUser(user);Long session=chat.session(user);
        if(session==null||!conversations.exists(user,session)){
            var rows=conversations.page(user,null,1);
            if(!rows.isEmpty())session=rows.getFirst().id();
            else{session=SnowflakeIdGenerator.getNextId();conversations.insert(session,user,"initial-"+session,common.now());}
            chat.session(user,session,common.now());
        }
        return read(user,session,before);
    }
    @Transactional(readOnly=true)
    public LumiChatDtos.Page read(long user,long session,Long before){
        requireUser(user);requireSession(user,session);var now=common.now();
        var rows=chat.page(user,session,before,21);boolean more=rows.size()>20;
        var page=new ArrayList<>(rows.subList(0,Math.min(rows.size(),20)));Collections.reverse(page);
        var active=chat.active(user);
        return new LumiChatDtos.Page(""+session,p.ready(),1000,remaining(user,now),page.stream().map(this::dto).toList(),more,more?""+page.getFirst().id():null,conversations.owned(user,session).title(),common.activeForUser(user)>0,active==null?null:""+active.sessionId(),active==null?null:""+active.id());
    }
    @Transactional(readOnly=true)
    public LumiChatDtos.Conversations list(long user,String cursor){
        requireUser(user);var rows=conversations.page(user,decodeCursor(cursor),21);boolean more=rows.size()>20;
        var page=rows.subList(0,Math.min(20,rows.size()));var active=chat.active(user);var last=page.isEmpty()?null:page.getLast();
        return new LumiChatDtos.Conversations(page.stream().map(row->new LumiChatDtos.Conversation(""+row.id(),row.title(),row.activityAt().atOffset(ZoneOffset.UTC).toString(),active!=null&&active.sessionId()==row.id())).toList(),more,more?encodeCursor(last):null,cp.getMaxConversations(),common.activeForUser(user)>0,active==null?null:""+active.sessionId());
    }
    @Transactional
    public LumiChatDtos.Page create(long user,String key){
        common.coordinate();requireUser(user);var existing=conversations.request(user,key);
        if(existing!=null){requireSession(user,existing.id());return read(user,existing.id(),null);}
        if(conversations.count(user)>=cp.getMaxConversations())throw problem("最多保留"+cp.getMaxConversations()+"段对话，请先删除不需要的对话");
        long id=SnowflakeIdGenerator.getNextId();conversations.insert(id,user,key,common.now());chat.session(user,id,common.now());return read(user,id,null);
    }
    @Transactional
    public void rename(long user,long session,String title){
        common.coordinate();requireUser(user);requireSession(user,session);title=title.strip();
        if(title.isBlank()||title.length()>40)throw problem("对话名称需为1至40个字符");conversations.rename(user,session,title);
    }
    @Transactional
    public void delete(long user,long session){
        common.coordinate();requireUser(user);var existing=conversations.owned(user,session);if(existing==null)throw missing();if(existing.deletedAt()!=null)return;
        if(chat.activeInSession(user,session)>0)throw problem("请先停止并等待此对话的回答结束，再删除");
        conversations.delete(user,session,common.now());chat.clear(user,session);
        if(Objects.equals(chat.session(user),session)){
            var latest=conversations.page(user,null,1);
            if(latest.isEmpty())chat.removePointer(user);else chat.session(user,latest.getFirst().id(),common.now());
        }
    }
    @Transactional
    public LumiChatDtos.Turn send(long user,LumiChatDtos.Send request){
        common.coordinate();requireUser(user);long session=parse(request.sessionId());requireSession(user,session);
        var existing=chat.lockRequest(user,request.requestKey());
        if(existing!=null){if(existing.sessionId()!=session||!existing.question().equals(request.content().trim()))throw problem("操作标识已用于其他内容，请刷新后重试");return dto(existing);}
        String question=request.content().trim();
        if(question.isEmpty()||question.length()>1000)throw problem("消息需为1至1000个字符");
        if(LumiIntent.requestsWeb(question))throw problem("Lumi 聊天暂不支持联网，可以先聊聊其他问题");
        var now=common.now();admit(user,now);long id=SnowflakeIdGenerator.getNextId();
        String title=question.replaceAll("\\s+"," ");conversations.touch(user,session,title.substring(0,Math.min(24,title.length())),now);
        chat.insert(id,user,session,request.requestKey(),question,now);return dto(chat.turn(id));
    }
    @Transactional
    public LumiChatDtos.Page reset(long user,String expected){
        // 兼容旧客户端的新对话入口，但不再销毁原对话；重放仍返回同一新会话。
        common.coordinate();requireUser(user);long session=parse(expected);requireSession(user,session);
        return create(user,"legacy-reset-"+expected);
    }
    @Transactional
    public LumiChatDtos.Turn retry(long id,long user){
        common.coordinate();requireUser(user);var turn=chat.lockTurn(id);
        if(turn==null||turn.userId()!=user)throw missing();requireSession(user,turn.sessionId());
        if(Set.of("PENDING","RUNNING","STOPPING","SUCCEEDED").contains(turn.status()))return dto(turn);
        if(!"FAILED".equals(turn.status())||turn.attempts()>=p.getMaxAttempts()||!chat.latestCurrent(turn))throw problem("只能重试当前对话最后一条失败消息");
        var now=common.now();admit(user,now);
        if(!chat.requeue(turn,now))throw problem("任务状态已变化，请刷新后重试");
        return dto(chat.lockTurn(id));
    }
    public record Work(LumiChatRepository.Turn turn,LumiRepository.Attempt attempt,String token){}
    public LumiChatDtos.Turn snapshot(long user,long id){
        var turn=chat.ownedTurn(user,id);if(turn==null)throw missing();
        return dto(turn);
    }
    @Transactional
    public LumiChatDtos.Turn stop(long user,long id){
        common.coordinate();requireUser(user);var turn=chat.lockTurn(id);
        if(turn==null||turn.userId()!=user)throw missing();requireSession(user,turn.sessionId());
        if("PENDING".equals(turn.status()))chat.finish(id,"STOPPED","已停止",turn.answer(),common.now());
        else if("RUNNING".equals(turn.status()))chat.stopping(id,common.now());
        return dto(chat.lockTurn(id));
    }
    @Transactional
    public boolean progress(Work work,String answer){
        if(answer==null||answer.length()>4000)return false;
        chat.lockUser(work.turn().userId());
        if(!current(work.turn()))return false;
        return chat.progress(work.turn().id(),work.token(),answer,common.now());
    }
    @Transactional
    public void checkpoint(Work work,String answer){
        if(answer==null||answer.length()>4000)return;
        chat.lockUser(work.turn().userId());
        if(current(work.turn()))chat.checkpoint(work.turn().id(),work.token(),answer,common.now());
    }
    @Transactional
    public Work claim(){
        common.coordinate();var now=common.now();chat.expirePending(now);
        for(var expired:chat.lockExpired(now)){budget.settleUnknown(expired.id());boolean stopped="STOPPING".equals(expired.status());chat.finish(expired.id(),stopped?"STOPPED":"FAILED",stopped?"已停止":"本次回答已中断，已保留生成内容",expired.answer(),now);}
        if(!p.ready()||common.allRunningCurrent()>=p.getConcurrency())return null;
        var turn=chat.lockPending();if(turn==null)return null;
        if(!current(turn)||turn.attempts()>=p.getMaxAttempts()){chat.finish(turn.id(),"FAILED","对话或账号已不可用",null,now);return null;}
        var attempt=budget.reserve(turn.id(),turn.attempts()+1,p,cp.getOutputTokens(),now);
        if(attempt==null){chat.finish(turn.id(),"FAILED","Lumi 今天的服务额度已用完",null,now);return null;}
        String token=UUID.randomUUID().toString();chat.start(turn,token,now,p.getLeaseSeconds());return new Work(chat.lockTurn(turn.id()),attempt,token);
    }
    @Transactional
    public boolean sending(Work work){
        chat.lockUser(work.turn().userId());common.coordinate();var turn=chat.lockTurn(work.turn().id());
        return p.ready()&&owns(turn,work)&&"RUNNING".equals(turn.status())&&current(turn)&&common.startAttempt(work.attempt().id(),turn.id());
    }
    @Transactional
    public void finish(Work work,DeepSeekClient.Result result){
        chat.lockUser(work.turn().userId());common.coordinate();var turn=chat.lockTurn(work.turn().id());
        // 已确认的费用即使账号或对话失效仍需结算；旧租约不能覆盖新任务结果。
        budget.settle(work.attempt(),result.inputTokens()==null||result.outputTokens()==null?null:p.cost(result.inputTokens(),result.outputTokens()),result.inputTokens(),result.outputTokens());
        if(!owns(turn,work))return;
        if(!p.ready()||!current(turn)){chat.finish(turn.id(),"FAILED","对话或账号状态已变化，未发布回答",null,common.now());return;}
        if("STOPPING".equals(turn.status())){chat.finish(turn.id(),"STOPPED","已停止",turn.answer(),common.now());return;}
        if(result.query()!=null||result.content()==null||result.content().isBlank()||result.content().length()>4000)throw problem("Lumi 未能生成长度合适的完整回答");
        chat.finish(turn.id(),"SUCCEEDED","Lumi 已回复",result.content(),common.now());
        conversations.activity(turn.userId(),turn.sessionId(),common.now());
    }
    @Transactional
    public void fail(Work work,String message){common.coordinate();var turn=chat.lockTurn(work.turn().id());if(!owns(turn,work))return;budget.settleUnknown(turn.id());boolean stopped="STOPPING".equals(turn.status());chat.finish(turn.id(),stopped?"STOPPED":"FAILED",stopped?"已停止":message,current(turn)?turn.answer():null,common.now());}
    private void admit(long user,LocalDateTime now){
        if(!p.ready())throw problem("Lumi 暂时不可用，请稍后重试");
        if(common.activeForUserCurrent(user)>0)throw problem("请等 Lumi 完成上一条回复");
        var last=chat.lastAcceptedCurrent(user);
        if(last!=null&&last.plusSeconds(cp.getCooldownSeconds()).isAfter(now))throw problem("发送有点快，请稍后重试");
        if(chat.dailyCurrent(user,common.day(now))>=cp.getUserDaily())throw problem("今天与 Lumi 聊天的次数已用完");
        if(common.allPendingCurrent()>=p.getQueueCapacity())throw problem("Lumi 现在有点忙，请稍后重试");
    }
    private int remaining(long user,LocalDateTime now){var last=chat.lastAccepted(user);return last==null?0:(int)Math.max(0,Duration.between(now,last.plusSeconds(cp.getCooldownSeconds())).toMillis()+999)/1000;}
    private void requireUser(long user){if(!chat.eligible(user))throw missing();}
    private void requireSession(long user,long session){if(!conversations.exists(user,session))throw missing();}
    private boolean current(LumiChatRepository.Turn t){return t!=null&&chat.eligible(t.userId())&&conversations.exists(t.userId(),t.sessionId());}
    private boolean owns(LumiChatRepository.Turn t,Work w){return t!=null&&Set.of("RUNNING","STOPPING").contains(t.status())&&Objects.equals(t.leaseToken(),w.token());}
    private LumiChatDtos.Turn dto(LumiChatRepository.Turn t){return new LumiChatDtos.Turn(""+t.id(),t.question(),t.answer(),t.status(),t.message(),"FAILED".equals(t.status())&&t.attempts()<p.getMaxAttempts()&&p.ready()&&chat.latest(t),t.createdAt().atOffset(ZoneOffset.UTC).toString(),""+t.revision(),""+t.sessionId());}
    private static String encodeCursor(LumiConversationRepository.Conversation row){return Base64.getUrlEncoder().withoutPadding().encodeToString((row.activityAt()+"|"+row.id()).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    private static LumiConversationRepository.Cursor decodeCursor(String cursor){
        if(cursor==null)return null;
        try{if(cursor.length()>128)throw new IllegalArgumentException();String[] parts=new String(Base64.getUrlDecoder().decode(cursor),java.nio.charset.StandardCharsets.UTF_8).split("\\|",-1);if(parts.length!=2)throw new IllegalArgumentException();long id=Long.parseLong(parts[1]);if(id<=0)throw new IllegalArgumentException();return new LumiConversationRepository.Cursor(LocalDateTime.parse(parts[0]),id);}
        catch(RuntimeException ex){throw new BusinessException(HttpStatus.BAD_REQUEST,"LUMI_CHAT_CURSOR_INVALID","会话列表游标无效，请重新加载");}
    }
    private static long parse(String id){try{return Long.parseLong(id);}catch(NumberFormatException ex){throw problem("对话编号无效");}}
    private static BusinessException problem(String m){return new BusinessException(HttpStatus.CONFLICT,"LUMI_CHAT_NOT_READY",m);}
    private static BusinessException missing(){return new BusinessException(HttpStatus.NOT_FOUND,"LUMI_CHAT_NOT_FOUND","这段对话暂不可用");}
}
