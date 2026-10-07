package com.luminous.api.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luminous.api.notification.NotificationEvent;
import org.apache.rocketmq.client.producer.*;
import org.apache.rocketmq.common.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;

/** 通知在发送前持久化，重投和本地降级复用同一个业务事件 ID。 */
@Component
public class NotificationMQProducer {
    private static final Logger log = LoggerFactory.getLogger(NotificationMQProducer.class);
    private final ObjectProvider<DefaultMQProducer> producers;
    private final NotificationTransactionService transactions;
    private final NotificationJournalService journal;
    private final ReliableEventRepository events;
    private final FailedMessageService failures;
    private final ObjectMapper mapper;
    @Value("${app.rocketmq.notification-topic:luminous-notifications}") private String topic;
    public NotificationMQProducer(ObjectProvider<DefaultMQProducer> producers, NotificationTransactionService transactions,
            NotificationJournalService journal, ReliableEventRepository events, FailedMessageService failures, ObjectMapper mapper) {
        this.producers = producers; this.transactions = transactions; this.journal = journal;
        this.events = events; this.failures = failures; this.mapper = mapper;
    }

    public void sendNotification(NotificationEvent event) {
        if (event == null) { return; }
        journal.enqueue(event);
        dispatch(event);
    }

    public void dispatch(NotificationEvent event) {
        try {
            DefaultMQProducer producer = producers.getIfAvailable();
            if (producer == null) { fallback(event); return; }
            Message message = new Message(topic, event.type(), event.eventId(), mapper.writeValueAsString(event).getBytes(StandardCharsets.UTF_8));
            producer.send(message, new SendCallback() {
                @Override public void onSuccess(SendResult result) {
                    if (result.getSendStatus() == SendStatus.SEND_OK) {
                        try { events.sent(event.eventId()); }
                        catch (Exception ex) { log.warn("通知发送水位更新失败，将复用原事件重投", ex); }
                    } else { fallback(event); }
                }
                @Override public void onException(Throwable error) { fallback(event); }
            });
        } catch (Exception ex) { fallback(event); }
    }

    private void fallback(NotificationEvent event) {
        try { transactions.process(event.eventId(), event); }
        catch (Exception ex) {
            log.error("通知降级失败，保存可查询、可重放的失败记录，eventId={}", event.eventId(), ex);
            try {
                events.retryLater(event.eventId(), ex.toString());
                int attempts = events.attempts(event.eventId());
                if (attempts >= 3) { failures.archive("NOTIFICATION", event.eventId(), mapper.writeValueAsString(event), attempts, ex); }
            }
            catch (Exception archiveError) { log.error("通知归档失败，原可靠事件保留等待重投", archiveError); }
        }
    }
}
