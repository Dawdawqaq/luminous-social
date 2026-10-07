package com.luminous.api.notification;

import com.luminous.api.common.AfterCommit;
import com.luminous.api.mq.NotificationJournalService;
import com.luminous.api.mq.NotificationMQProducer;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 同步记录事件保证回滚一致性，提交后投递；进程中断由可靠事件扫描补发。 */
@Component
public class NotificationEventListener {
    private final NotificationMQProducer producer;
    private final NotificationJournalService journal;
    public NotificationEventListener(NotificationMQProducer producer, NotificationJournalService journal) {
        this.producer = producer; this.journal = journal;
    }
    @EventListener
    public void onNotificationEvent(NotificationEvent event) {
        journal.enqueueInTransaction(event);
        AfterCommit.run(() -> producer.dispatch(event));
    }
}
