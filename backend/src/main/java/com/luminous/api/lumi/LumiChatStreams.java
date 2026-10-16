package com.luminous.api.lumi;

import com.luminous.api.common.BusinessException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** 有界只读订阅发送持久快照，断线重连不会创建任务或重复调用模型。 */
@Component
public class LumiChatStreams implements DisposableBean {
    private final LumiChatService service;
    private final Map<Long,Integer> users=new HashMap<>();
    private int subscribers;
    private final ExecutorService executor=Executors.newVirtualThreadPerTaskExecutor();
    public LumiChatStreams(LumiChatService service){this.service=service;}
    public SseEmitter open(long user,long id){
        service.snapshot(user,id);
        synchronized(users){
            if(subscribers>=32||users.getOrDefault(user,0)>=2)throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS,"LUMI_CHAT_STREAM_BUSY","连接较多，正在改用定时更新");
            subscribers++;users.merge(user,1,Integer::sum);
        }
        var emitter=new SseEmitter(90_000L);var closed=new AtomicBoolean();
        emitter.onCompletion(()->closed.set(true));emitter.onTimeout(()->{closed.set(true);emitter.complete();});emitter.onError(ex->closed.set(true));
        try{executor.submit(()->{
            String previous=null;long heartbeat=System.nanoTime(),deadline=System.nanoTime()+85_000_000_000L;
            try{
                while(!closed.get()&&System.nanoTime()<deadline){
                    var turn=service.snapshot(user,id);
                    if(!Objects.equals(previous,turn.revision())){emitter.send(SseEmitter.event().name("turn").id(turn.revision()).data(turn));previous=turn.revision();}
                    if(!Set.of("PENDING","RUNNING","STOPPING").contains(turn.status()))break;
                    if(System.nanoTime()-heartbeat>5_000_000_000L){emitter.send(SseEmitter.event().comment("keepalive"));heartbeat=System.nanoTime();}
                    Thread.sleep(500);
                }
                emitter.complete();
            }catch(InterruptedException ex){Thread.currentThread().interrupt();emitter.complete();}
            catch(Exception ex){emitter.complete();}
            finally{release(user);}
        });}catch(RejectedExecutionException ex){release(user);emitter.complete();}
        return emitter;
    }
    private void release(long user){synchronized(users){subscribers--;users.computeIfPresent(user,(key,value)->value<=1?null:value-1);}}
    @Override public void destroy(){executor.shutdownNow();}
}
