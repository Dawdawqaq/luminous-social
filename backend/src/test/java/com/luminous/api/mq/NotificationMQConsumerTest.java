package com.luminous.api.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luminous.api.notification.NotificationEvent;
import org.apache.rocketmq.client.consumer.listener.*;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationMQConsumerTest {
    @Mock private NotificationTransactionService transactions;
    @Mock private FailedMessageService failures;
    private final ObjectMapper mapper = new ObjectMapper();
    private NotificationMQConsumer consumer;
    @BeforeEach void setUp() { consumer = new NotificationMQConsumer(transactions, failures, mapper); }
    private MessageExt message(NotificationEvent event, int retries) throws Exception {
        MessageExt message = new MessageExt(); message.setMsgId("[E2E_TEST]-broker"); message.setKeys(event.eventId());
        message.setBody(mapper.writeValueAsBytes(event)); message.setReconsumeTimes(retries); return message;
    }
    @Test void consumesThroughTransactionService() throws Exception {
        NotificationEvent event = new NotificationEvent(1L, 2L, "COMMENT_POST", "POST", 100L, "[E2E_TEST]");
        assertThat(consumer.consumeMessage(List.of(message(event, 0)), null)).isEqualTo(ConsumeConcurrentlyStatus.CONSUME_SUCCESS);
        verify(transactions).process(event.eventId(), event);
    }
    @Test void archiveFailureDoesNotAcknowledge() throws Exception {
        NotificationEvent event = new NotificationEvent(1L, 2L, "COMMENT_POST", "POST", 100L, "[E2E_TEST]");
        doThrow(new IllegalStateException("[E2E_TEST] 业务失败")).when(transactions).process(anyString(), any());
        doThrow(new IllegalStateException("[E2E_TEST] 归档失败")).when(failures).archive(anyString(),anyString(),anyString(),anyInt(),any());
        assertThat(consumer.consumeMessage(List.of(message(event, 3)), null)).isEqualTo(ConsumeConcurrentlyStatus.RECONSUME_LATER);
    }
    @Test void archivedFailureDoesNotSkipRestOfBatch() throws Exception {
        NotificationEvent first = new NotificationEvent(1L, 2L, "COMMENT_POST", "POST", 100L, "[E2E_TEST] 失败");
        NotificationEvent second = new NotificationEvent(1L, 2L, "COMMENT_POST", "POST", 100L, "[E2E_TEST] 成功");
        doThrow(new IllegalStateException("[E2E_TEST]")).when(transactions).process(first.eventId(), first);
        assertThat(consumer.consumeMessage(List.of(message(first, 3), message(second, 0)), null)).isEqualTo(ConsumeConcurrentlyStatus.CONSUME_SUCCESS);
        verify(failures).archive(eq("NOTIFICATION"),eq(first.eventId()),anyString(),eq(4),any());
        verify(transactions).process(second.eventId(), second);
    }
}
