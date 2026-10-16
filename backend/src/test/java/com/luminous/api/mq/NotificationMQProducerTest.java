package com.luminous.api.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luminous.api.notification.NotificationEvent;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationMQProducerTest {
    @Mock private ObjectProvider<DefaultMQProducer> producers;
    @Mock private DefaultMQProducer producer;
    @Mock private NotificationTransactionService transactions;
    @Mock private NotificationJournalService journal;
    @Mock private ReliableEventRepository events;
    @Mock private FailedMessageService failures;
    @Test void fallbackSharesDurableEventIdentity() {
        NotificationEvent event = new NotificationEvent(1L,2L,"COMMENT_POST","POST",100L,"[E2E_TEST]");
        NotificationMQProducer sender = new NotificationMQProducer(producers,transactions,journal,events,failures,new ObjectMapper().findAndRegisterModules());
        sender.sendNotification(event);
        var ordered = inOrder(journal,transactions);
        ordered.verify(journal).enqueue(event);
        ordered.verify(transactions).process(event.eventId(),event);
    }
    @Test void sendsDurableEventWhenBrokerAvailable() throws Exception {
        when(producers.getIfAvailable()).thenReturn(producer);
        NotificationEvent event = new NotificationEvent(1L,2L,"COMMENT_POST","POST",100L,"[E2E_TEST]");
        new NotificationMQProducer(producers,transactions,journal,events,failures,new ObjectMapper().findAndRegisterModules()).sendNotification(event);
        verify(journal).enqueue(event);
        verify(producer).send(any(org.apache.rocketmq.common.message.Message.class),any(org.apache.rocketmq.client.producer.SendCallback.class));
    }
}
