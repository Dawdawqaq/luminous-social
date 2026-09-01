package com.luminous.api.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luminous.api.notification.NotificationEvent;
import com.luminous.api.notification.NotificationService;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 消息通知 RocketMQ 生产者
 * 负责将领域通知事件投递至 RocketMQ，具备自动降级与异步发送机制
 */
@Component
public class NotificationMQProducer {

    private static final Logger log = LoggerFactory.getLogger(NotificationMQProducer.class);

    private final ObjectProvider<DefaultMQProducer> producerProvider;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @Value("${app.rocketmq.notification-topic:luminous-notifications}")
    private String notificationTopic;

    public NotificationMQProducer(
            ObjectProvider<DefaultMQProducer> producerProvider,
            NotificationService notificationService,
            ObjectMapper objectMapper
    ) {
        this.producerProvider = producerProvider;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    /**
     * 发送通知消息至 RocketMQ（若未开启 RocketMQ 则自动降级为本地直接处理）
     */
    public void sendNotification(NotificationEvent event) {
        if (event == null) return;

        DefaultMQProducer producer = producerProvider.getIfAvailable();
        if (producer == null) {
            // 降级模式：直接调用 Service 处理
            log.debug("RocketMQ producer not available, falling back to direct service execution for event: {}", event.type());
            notificationService.handleEvent(event);
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(event);
            String tag = event.type() != null ? event.type() : "GENERAL";
            String key = event.actorId() + ":" + event.targetId() + ":" + System.currentTimeMillis();

            Message msg = new Message(
                    notificationTopic,
                    tag,
                    key,
                    json.getBytes(StandardCharsets.UTF_8)
            );

            producer.send(msg, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    log.debug("Notification message sent successfully: msgId={}, status={}", sendResult.getMsgId(), sendResult.getSendStatus());
                }

                @Override
                public void onException(Throwable e) {
                    log.error("Failed to send notification message asynchronously via RocketMQ, executing local fallback", e);
                    notificationService.handleEvent(event);
                }
            });
        } catch (Exception e) {
            log.error("Error serializing or dispatching message to RocketMQ, falling back to direct service", e);
            notificationService.handleEvent(event);
        }
    }
}
