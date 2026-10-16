package com.luminous.api.lumi;

import com.luminous.api.common.BusinessException;
import com.luminous.api.common.SnowflakeIdGenerator;
import com.luminous.api.notification.NotificationEvent;
import com.luminous.api.cache.PostCacheService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;

/** 任务准入、费用预占和最终回复在短事务内完成，模型请求由外部工作器执行。 */
@Service
public class LumiTaskService {
    private static final String COUNTED="status IN ('PENDING','RUNNING','SUCCEEDED')";
    private final LumiRepository store;
    private final LumiProperties p;
    private final ApplicationEventPublisher events;
    private final PostCacheService cache;
    public LumiTaskService(LumiRepository store,LumiProperties p,ApplicationEventPublisher events,PostCacheService cache) {
        this.store=store;this.p=p;this.events=events;this.cache=cache;p.validate();
    }
    @EventListener
    @Transactional(propagation=Propagation.MANDATORY)
    public void requested(LumiDtos.Requested event) {
        if(store.forComment(event.commentId())!=null) return;
        var row=store.request(event.commentId());if(row==null)return;
        String question=LumiIntent.question(row.content());
        if(question==null) return;
        long user=row.userId(),post=row.postId();
        store.coordinate();
        if(store.lockForComment(event.commentId())!=null)return;
        var now=store.now();
        String denial=denial(user,post,event.commentId(),question,now);
        store.insert(SnowflakeIdGenerator.getNextId(),event.commentId(),post,user,now,denial==null?"PENDING":"REJECTED",denial==null?null:"LUMI_REJECTED",denial==null?"Lumi 正在排队":denial);
    }
    private String denial(long user,long post,long comment,String question,LocalDateTime now) {
        if(!p.ready()) return "Lumi 暂时不可用，请稍后再试";
        if(question.isBlank()) return "请在 @Lumi 后写下关于帖子的具体问题";
        if(LumiIntent.requestsWeb(question)) return "Lumi 暂未接入联网搜索，可以先询问帖子内容";
        if(store.context(comment)==null) return "Lumi 仅能处理公开且正常的帖子，原评论和楼层须仍存在";
        if(store.activeForUserCurrent(user)>0) return "Lumi 正在处理你的上一条请求";
        LocalDateTime last=store.lastAcceptedCurrent(user);
        if(last!=null&&last.plusSeconds(p.getCooldownSeconds()).isAfter(now)) return "调用有点频繁，请稍后再试";
        var day=store.day(now);
        if(store.countCurrent("user_id=? AND quota_day=? AND "+COUNTED,user,day)>=p.getUserDaily()) return "今天调用 Lumi 的次数已用完";
        if(store.countCurrent("user_id=? AND post_id=? AND quota_day=? AND "+COUNTED,user,post,day)>=p.getUserPostDaily()) return "今天在这篇帖子调用 Lumi 的次数已用完";
        if(store.countCurrent("post_id=? AND quota_day=? AND "+COUNTED,post,day)>=p.getPostDaily()) return "这篇帖子今天调用 Lumi 的次数已用完";
        if(store.allPendingCurrent()>=p.getQueueCapacity()) return "Lumi 现在有点忙，请稍后再试";
        return null;
    }
    @Transactional(readOnly=true)
    public LumiDtos.Status status(long post,long user) {
        Boolean publicPost=store.readablePost(post,user);
        if(publicPost==null)throw missing();
        return new LumiDtos.Status(p.ready()&&publicPost,"Lumi",false,store.own(post,user).stream().map(this::dto).toList());
    }
    private LumiDtos.Task dto(LumiRepository.Task t) {
        return new LumiDtos.Task(""+t.id(),""+t.commentId(),t.status(),t.message(),t.replyId()==null?null:""+t.replyId(),
                "FAILED".equals(t.status())&&t.attempts()<p.getMaxAttempts()&&p.ready());
    }
    @Transactional
    public LumiDtos.Task retry(long id,long user) {
        var before=store.task(id);
        if(before==null||before.userId()!=user) throw missing();
        if(store.lockPost(before.postId())==null) throw missing();
        store.coordinate(); var task=store.lockTask(id);
        if(task==null||task.userId()!=user)throw missing();
        if("PENDING".equals(task.status())||"RUNNING".equals(task.status())||"SUCCEEDED".equals(task.status())) return dto(task);
        if(!"FAILED".equals(task.status())||task.attempts()>=p.getMaxAttempts()) throw problem("这条请求暂时不能重试");
        var context=store.context(task.commentId());
        if(context==null) throw missing();
        var now=store.now();String denial=denial(user,task.postId(),task.commentId(),LumiIntent.question(context.question()),now);
        if(denial!=null) throw problem(denial);
        if(!store.requeue(task,now))throw problem("任务状态已变化，请刷新后重试");
        return dto(store.lockTask(id));
    }
    public record Work(LumiRepository.Task task,LumiRepository.Attempt attempt,String token) {}
    @Transactional
    public Work claim() {
        store.coordinate();var now=store.now();
        store.expirePending(now);
        for(var expired:store.lockExpired(now)) {
            store.settleUnknown(expired.id());store.state(expired.id(),"FAILED","LUMI_INTERRUPTED","执行已中断，稍后可重试",null);
        }
        if(!p.ready()) return null;
        if(store.allRunningCurrent()>=p.getConcurrency()) return null;
        var task=store.lockPending();if(task==null)return null;
        if(task.attempts()>=p.getMaxAttempts()) {store.state(task.id(),"FAILED","LUMI_ATTEMPTS","重试次数已用完",null);return null;}
        var attempt=store.reserve(task,p,now);
        if(attempt==null) {store.state(task.id(),"FAILED","LUMI_BUDGET","Lumi 今天的服务额度已用完",null);return null;}
        String token=UUID.randomUUID().toString();store.start(task,token,now,p.getLeaseSeconds());
        return new Work(store.lockTask(task.id()),attempt,token);
    }
    @Transactional
    public LumiRepository.Attempt next(Work work) {
        store.coordinate();var task=store.lockTask(work.task().id());
        if(!owns(task,work)||task.attempts()>=p.getMaxAttempts()) return null;
        return store.reserve(task,p,store.now());
    }
    @Transactional
    public void usage(LumiRepository.Attempt a,Integer input,Integer output) {
        store.coordinate();Long cost=input==null||output==null?null:p.cost(input,output);store.settle(a,cost,input,output);
    }
    @Transactional
    public boolean sending(Work work,LumiRepository.Attempt attempt,List<Long> sources) {
        if(!lockContent(work,sources))return false;
        store.coordinate();var task=store.lockTask(work.task().id());
        if(!p.ready()||!owns(task,work)||store.context(task.commentId())==null||!visibleSources(sources)) return false;
        return store.startAttempt(attempt.id(),task.id());
    }
    @Transactional
    public void fail(Work work,String code,String message,boolean cancelled) {
        store.coordinate();var task=store.lockTask(work.task().id());if(!owns(task,work))return;
        store.settleUnknown(task.id());store.state(task.id(),cancelled?"CANCELLED":"FAILED",code,message,null);
    }
    @Transactional
    public boolean publish(Work work,String answer,List<Long> sources) {
        if(answer==null||answer.isBlank()||answer.length()>150) throw problem("Lumi 回复超出长度限制");
        // 多篇来源按编号顺序锁定，避免两个任务交叉引用时互相等待；随后才获取全局短锁。
        if(!lockContent(work,sources)) {fail(work,"LUMI_REMOVED","原帖或来源已不可用",true);return false;}
        store.coordinate();var task=store.lockTask(work.task().id());
        if(!owns(task,work))return false;
        var context=store.context(task.commentId());
        if(!p.ready()||context==null||!visibleSources(sources)) {store.settleUnknown(task.id());store.state(task.id(),"CANCELLED","LUMI_REMOVED","原内容或账号状态已变化，未发布回复",null);return false;}
        long reply=SnowflakeIdGenerator.getNextId();
        store.insertReply(reply,task,context.rootId(),answer);
        cache.invalidatePost(task.postId());
        events.publishEvent(NotificationEvent.interaction(LumiProperties.BOT_ID,task.userId(),"REPLY_COMMENT","POST",task.postId(),answer,task.postId(),reply));
        store.settleUnknown(task.id());store.state(task.id(),"SUCCEEDED",null,"Lumi 已回复",reply);
        return true;
    }
    public boolean visibleSources(List<Long> ids) {
        for(long id:ids) if(!store.visiblePost(id)) return false;
        return true;
    }
    private boolean owns(LumiRepository.Task task,Work work) {return task!=null&&"RUNNING".equals(task.status())&&Objects.equals(task.leaseToken(),work.token());}
    private boolean lockContent(Work work,List<Long> sources) {
        var ids=new TreeSet<>(sources);ids.add(work.task().postId());
        for(long id:ids)if(store.lockPost(id)==null)return false;
        store.lockPeople(work.task());return true;
    }
    private static BusinessException missing() {return new BusinessException(HttpStatus.NOT_FOUND,"LUMI_TASK_NOT_FOUND","这条 Lumi 请求或原内容已不可用");}
    private static BusinessException problem(String message) {return new BusinessException(HttpStatus.CONFLICT,"LUMI_NOT_READY",message);}
}
