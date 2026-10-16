package com.luminous.api.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luminous.api.notification.NotificationEvent;
import com.luminous.api.notification.NotificationService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

/** 通知消费记录、数据库防抖与通知插入在同一事务内完成。 */
@Service
public class NotificationTransactionService {
    private final ReliableEventRepository events;
    private final NotificationService notifications;
    private final ObjectMapper mapper;
    private final JdbcTemplate jdbc;
    public NotificationTransactionService(ReliableEventRepository events, NotificationService notifications, ObjectMapper mapper, JdbcTemplate jdbc) {
        this.events = events; this.notifications = notifications; this.mapper = mapper; this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(String eventId, NotificationEvent event) {
        processBatch(java.util.List.of(new Delivery(eventId,event)));
    }

    public record Delivery(String eventId,NotificationEvent event) {}

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processBatch(java.util.List<Delivery> deliveries) {
        if(deliveries==null || deliveries.isEmpty() || deliveries.size()>64) throw new IllegalArgumentException("通知批次应为一至六十四条");
        java.util.List<NotificationEvent> accepted=new java.util.ArrayList<>();
        // 消费记录按事件编号加锁，批次间保持相同顺序；不再锁定接收者用户行。
        for(Delivery delivery : deliveries.stream().sorted(java.util.Comparator.comparing(Delivery::eventId)).toList()) {
            if(accept(delivery.eventId(),delivery.event())) accepted.add(delivery.event());
        }
        notifications.handleEvents(accepted);
        for(Delivery delivery : deliveries) events.consumed(delivery.eventId());
    }

    private boolean accept(String eventId,NotificationEvent event) {
        if (eventId == null || eventId.isBlank() || event == null || event.actorId() == null || event.receiverId() == null) {
            throw new IllegalArgumentException("通知事件缺少身份或接收者");
        }
        if (jdbc.queryForList("SELECT id FROM users WHERE id=?", Long.class, event.receiverId()).isEmpty()) {
            throw new IllegalArgumentException("通知接收者不存在");
        }
        try {
            ReliableEventRepository.Event durable = events.find(eventId);
            if (durable == null) {
                events.insert(new ReliableEventRepository.Event(eventId, "NOTIFICATION", null, event.receiverId(), 0,
                        mapper.writeValueAsString(event), "PENDING"));
            } else if (!"NOTIFICATION".equals(durable.eventType())
                    || !mapper.readValue(durable.payload(), NotificationEvent.class).equals(event)) {
                throw new IllegalArgumentException("通知消息与持久化事件不一致");
            }
        } catch (java.io.IOException ex) { throw new IllegalStateException("通知事件序列化失败", ex); }
        return events.recordConsumption("NOTIFICATION", eventId);
    }
}
