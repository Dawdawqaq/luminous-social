package com.luminous.api.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 普通互动按最后更新保留一百八十天，系统通知不参与自动清理；每轮有界执行。 */
@Component
public class NotificationRetentionJob {
    private final NotificationInboxRepository inbox;
    private final int days;
    private final int maxBatches;
    public NotificationRetentionJob(NotificationInboxRepository inbox,
            @Value("${app.notifications.retention-days:180}") int days,
            @Value("${app.notifications.cleanup-max-batches:20}") int maxBatches) {
        if(days<1) throw new IllegalArgumentException("通知保留天数必须为正数");
        if(maxBatches<1 || maxBatches>100) throw new IllegalArgumentException("清理批次数应为一至一百");
        this.inbox=inbox; this.days=days; this.maxBatches=maxBatches;
    }
    @Scheduled(fixedDelayString="${app.notifications.cleanup-delay-ms:3600000}",initialDelayString="${app.notifications.cleanup-delay-ms:3600000}")
    public void cleanup() { for(int i=0;i<maxBatches;i++) if(inbox.purge(days)==0) break; }
}
