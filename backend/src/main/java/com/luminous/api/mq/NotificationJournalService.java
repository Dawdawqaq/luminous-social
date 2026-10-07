package com.luminous.api.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luminous.api.notification.NotificationEvent;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 通知领域事件与业务事务一起持久化；独立发送入口仍可创建新的投递事务。 */
@Service
public class NotificationJournalService {
    private final ReliableEventRepository events;
    private final ObjectMapper mapper;
    public NotificationJournalService(ReliableEventRepository events, ObjectMapper mapper) { this.events = events; this.mapper = mapper; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enqueue(NotificationEvent event) { insert(event); }

    @Transactional
    public void enqueueInTransaction(NotificationEvent event) { insert(event); }

    private void insert(NotificationEvent event) {
        if (event.eventId() == null || event.eventId().isBlank()) { throw new IllegalArgumentException("通知事件 ID 不能为空"); }
        try {
            events.insert(new ReliableEventRepository.Event(event.eventId(), "NOTIFICATION", null, event.receiverId(), 0,
                    mapper.writeValueAsString(event), "PENDING"));
        } catch (DuplicateKeyException duplicate) {
            // 相同业务事件重发时使用原有投递记录，不生成新的幂等身份。
        } catch (java.io.IOException ex) { throw new IllegalStateException("通知事件序列化失败", ex); }
    }
}
