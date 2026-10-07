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
        if (eventId == null || eventId.isBlank() || event == null || event.actorId() == null || event.receiverId() == null) {
            throw new IllegalArgumentException("通知事件缺少身份或接收者");
        }
        // 在首次一致性读取之前锁定接收者，防抖和重复消费均以数据库提交为准。
        if (jdbc.queryForList("SELECT id FROM users WHERE id=? FOR UPDATE", Long.class, event.receiverId()).isEmpty()) {
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
        if (!events.recordConsumption("NOTIFICATION", eventId)) { events.consumed(eventId); return; }
        notifications.handleEvent(event);
        events.consumed(eventId);
    }
}
