package com.luminous.api.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luminous.api.vote.VoteTransactionService;
import com.luminous.api.vote.VoteProjectionService;
import org.apache.rocketmq.client.producer.*;
import org.apache.rocketmq.common.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;

/** 只发送已经持久化的事件；正常消费与发送超时后的降级共用同一事件 ID。 */
@Service
public class VoteMQProducer {
    private static final Logger log = LoggerFactory.getLogger(VoteMQProducer.class);
    private final ObjectProvider<DefaultMQProducer> producers;
    private final ObjectMapper mapper;
    private final VoteTransactionService transactions;
    private final VoteProjectionService projection;
    private final ReliableEventRepository events;
    @Value("${app.rocketmq.vote-topic:luminous-votes-topic}") private String topic;

    public VoteMQProducer(ObjectProvider<DefaultMQProducer> producers, ObjectMapper mapper,
                          VoteTransactionService transactions, VoteProjectionService projection, ReliableEventRepository events) {
        this.producers = producers; this.mapper = mapper; this.transactions = transactions; this.projection = projection; this.events = events;
    }

    public void sendVoteMessage(VoteMessage vote) {
        try {
            DefaultMQProducer producer = producers.getIfAvailable();
            if (producer == null) { fallback(vote, null); return; }
            Message message = new Message(topic, "POST_VOTE", vote.msgId(), mapper.writeValueAsString(vote).getBytes(StandardCharsets.UTF_8));
            producer.send(message, new SendCallback() {
                @Override public void onSuccess(SendResult result) {
                    if (result.getSendStatus() == SendStatus.SEND_OK) {
                        try { events.sent(vote.msgId()); }
                        catch (Exception ex) { log.warn("投票已发送但发送水位保存失败，允许按同一事件重投", ex); }
                    } else { fallback(vote, new IllegalStateException("Broker 未确认消息持久化")); }
                }
                @Override public void onException(Throwable error) { fallback(vote, error); }
            });
        } catch (Exception ex) { fallback(vote, ex); }
    }

    private void fallback(VoteMessage vote, Throwable error) {
        try {
            transactions.process(vote);
            // 数据库事务已经提交，缓存异常不撤销已接收的业务操作。
            try { projection.refreshCommitted(vote.postId()); }
            catch (Exception ex) { log.warn("降级落库完成，投票缓存等待对账恢复", ex); }
        } catch (Exception ex) {
            log.error("投票降级失败，事件保留在数据库中等待重投，eventId={}", vote.msgId(), ex);
            try { events.retryLater(vote.msgId(), ex.toString()); }
            catch (Exception journalError) { log.error("重投时间更新失败，原待处理事件仍保留", journalError); }
        }
    }
}
