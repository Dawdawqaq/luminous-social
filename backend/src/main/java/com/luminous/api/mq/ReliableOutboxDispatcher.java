package com.luminous.api.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luminous.api.notification.NotificationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 重启后扫描未消费事件；Broker 已接收但未确认的事件允许重复发送。 */
@Component
public class ReliableOutboxDispatcher {
    private static final Logger log = LoggerFactory.getLogger(ReliableOutboxDispatcher.class);
    private final ReliableEventRepository events;
    private final VoteMQProducer votes;
    private final NotificationMQProducer notifications;
    private final ObjectMapper mapper;
    @Value("${app.reliable-events.dispatch-enabled:true}") private boolean enabled;
    public ReliableOutboxDispatcher(ReliableEventRepository events, VoteMQProducer votes,
                                    NotificationMQProducer notifications, ObjectMapper mapper) {
        this.events = events; this.votes = votes; this.notifications = notifications; this.mapper = mapper;
    }
    @Scheduled(fixedDelayString="${app.reliable-events.retry-delay-ms:5000}")
    public void scheduledRetry() { if (enabled) { retryPending(); } }

    public void retryPending() {
        for (ReliableEventRepository.Event event : events.due()) {
            try {
                if ("VOTE".equals(event.eventType())) { votes.sendVoteMessage(mapper.readValue(event.payload(), VoteMessage.class)); }
                else if ("NOTIFICATION".equals(event.eventType())) { notifications.dispatch(mapper.readValue(event.payload(), NotificationEvent.class)); }
                else { throw new IllegalArgumentException("未知可靠事件类型"); }
            } catch (Exception ex) {
                log.error("可靠事件重投失败，eventId={}", event.eventId(), ex);
                try { events.retryLater(event.eventId(), ex.toString()); }
                catch (Exception updateError) { log.error("可靠事件仍保留，下一轮继续重投", updateError); }
            }
        }
    }
}
