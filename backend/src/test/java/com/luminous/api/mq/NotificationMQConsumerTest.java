package com.luminous.api.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luminous.api.notification.NotificationEvent;
import com.luminous.api.notification.NotificationService;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationMQConsumerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ConsumeConcurrentlyContext context;

    private ObjectMapper objectMapper = new ObjectMapper();
    private NotificationMQConsumer consumer;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        consumer = new NotificationMQConsumer(notificationService, redisTemplate, objectMapper);
    }

    @Test
    void consumeMessage_ConsumesSuccessfully_WhenFirstTime() throws Exception {
        NotificationEvent event = new NotificationEvent(
                1L, 2L, "LIKE_POST", "POST", 100L, "Sample Post"
        );
        String json = objectMapper.writeValueAsString(event);

        MessageExt msg = new MessageExt();
        msg.setMsgId("MSG_12345");
        msg.setBody(json.getBytes(StandardCharsets.UTF_8));

        when(valueOperations.setIfAbsent(eq("mq:dedup:notification:MSG_12345"), eq("PROCESSING"), any(Duration.class)))
                .thenReturn(true);

        ConsumeConcurrentlyStatus status = consumer.consumeMessage(List.of(msg), context);

        assertThat(status).isEqualTo(ConsumeConcurrentlyStatus.CONSUME_SUCCESS);
        verify(notificationService, times(1)).handleEvent(any(NotificationEvent.class));
    }

    @Test
    void consumeMessage_SkipsDuplicate_WhenAlreadyProcessed() {
        MessageExt msg = new MessageExt();
        msg.setMsgId("MSG_DUPLICATE");
        msg.setBody("{}".getBytes(StandardCharsets.UTF_8));

        when(valueOperations.setIfAbsent(eq("mq:dedup:notification:MSG_DUPLICATE"), eq("PROCESSING"), any(Duration.class)))
                .thenReturn(false);

        ConsumeConcurrentlyStatus status = consumer.consumeMessage(List.of(msg), context);

        assertThat(status).isEqualTo(ConsumeConcurrentlyStatus.CONSUME_SUCCESS);
        verify(notificationService, never()).handleEvent(any());
    }
}
