package com.luminous.api.notification;

import com.luminous.api.mq.NotificationMQProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final NotificationMQProducer notificationMQProducer;

    public NotificationEventListener(NotificationMQProducer notificationMQProducer) {
        this.notificationMQProducer = notificationMQProducer;
    }

    @Async("notificationExecutor")
    @EventListener
    public void onNotificationEvent(NotificationEvent event) {
        try {
            notificationMQProducer.sendNotification(event);
        } catch (Exception e) {
            log.error("Failed to process notification event via MQ: {}", event, e);
        }
    }
}
