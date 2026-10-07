package com.luminous.api.security;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 仅删除保留期结束的已完成记录，不删除正在执行的业务。 */
@Component
public class IdempotencyCleanupScheduler {
    private final IdempotencyRepository repository;
    public IdempotencyCleanupScheduler(IdempotencyRepository repository) { this.repository = repository; }
    @Scheduled(fixedDelayString="${app.idempotency.cleanup-delay-ms:3600000}")
    public void cleanup() { repository.purgeExpired(); }
}
