package com.luminous.api.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luminous.api.notification.NotificationEvent;
import com.luminous.api.notification.NotificationService;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationMQProducerTest {

    @Mock
    private ObjectProvider<DefaultMQProducer> producerProvider;

    @Mock
    private DefaultMQProducer defaultMQProducer;

    @Mock
    private NotificationService notificationService;

    private ObjectMapper objectMapper = new ObjectMapper();
    private NotificationMQProducer notificationMQProducer;

    @BeforeEach
    void setUp() {
        notificationMQProducer = new NotificationMQProducer(producerProvider, notificationService, objectMapper);
    }

    @Test
    void sendNotification_FallsBackToService_WhenProducerNotAvailable() {
        when(producerProvider.getIfAvailable()).thenReturn(null);

        NotificationEvent event = new NotificationEvent(
                1L, 2L, "LIKE_POST", "POST", 100L, "Sample Post"
        );

        notificationMQProducer.sendNotification(event);

        verify(notificationService, times(1)).handleEvent(event);
    }

    @Test
    void sendNotification_SendsMessage_WhenProducerAvailable() throws Exception {
        when(producerProvider.getIfAvailable()).thenReturn(defaultMQProducer);

        NotificationEvent event = new NotificationEvent(
                1L, 2L, "COMMENT_POST", "POST", 100L, "Sample Comment"
        );

        notificationMQProducer.sendNotification(event);

        verify(defaultMQProducer, times(1)).send(any(org.apache.rocketmq.common.message.Message.class), any(org.apache.rocketmq.client.producer.SendCallback.class));
    }
}
