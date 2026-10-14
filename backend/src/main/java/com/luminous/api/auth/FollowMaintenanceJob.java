package com.luminous.api.auth;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 定期有界删除过期额度和通知窗口，不删除有效关注关系。 */
@Component
public class FollowMaintenanceJob {
    private final FollowActionRepository repository;
    public FollowMaintenanceJob(FollowActionRepository repository) { this.repository = repository; }
    @Scheduled(fixedDelay=60000,initialDelay=60000)
    public void cleanup() {
        for(int i=0;i<10;i++) if(repository.purgeExpired()==0) break;
    }
}
