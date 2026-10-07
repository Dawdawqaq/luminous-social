package com.luminous.api.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luminous.api.notification.NotificationEvent;
import org.apache.rocketmq.client.consumer.listener.*;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** 达到重试阈值时先归档原消息，归档失败则继续请求 Broker 重投。 */
@Component
public class NotificationMQConsumer implements MessageListenerConcurrently {
    private static final Logger log = LoggerFactory.getLogger(NotificationMQConsumer.class);
    private final NotificationTransactionService transactions;
    private final FailedMessageService failures;
    private final ObjectMapper mapper;
    public NotificationMQConsumer(NotificationTransactionService transactions, FailedMessageService failures, ObjectMapper mapper) {
        this.transactions = transactions; this.failures = failures; this.mapper = mapper;
    }
    @Override
    public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> messages, ConsumeConcurrentlyContext context) {
        if (messages == null) { return ConsumeConcurrentlyStatus.CONSUME_SUCCESS; }
        for (MessageExt message : messages) {
            String eventId = message.getKeys() == null || message.getKeys().isBlank() ? message.getMsgId() : message.getKeys();
            String payload = new String(message.getBody(), StandardCharsets.UTF_8);
            try {
                NotificationEvent event = mapper.readValue(payload, NotificationEvent.class);
                if (event.eventId() != null && !event.eventId().isBlank()) { eventId = event.eventId(); }
                transactions.process(eventId, event);
            } catch (Exception ex) {
                log.error("通知消费失败，eventId={}，retryTimes={}", eventId, message.getReconsumeTimes(), ex);
                if (message.getReconsumeTimes() < 3) { return ConsumeConcurrentlyStatus.RECONSUME_LATER; }
                try { failures.archive("NOTIFICATION", eventId, payload, message.getReconsumeTimes() + 1, ex); }
                catch (Exception archiveError) {
                    log.error("失败通知归档失败，禁止确认消费，eventId={}", eventId, archiveError);
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                }
                // 继续处理批次中的后续消息，不能因为一条失败消息提前确认整个批次。
            }
        }
        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
    }
}
