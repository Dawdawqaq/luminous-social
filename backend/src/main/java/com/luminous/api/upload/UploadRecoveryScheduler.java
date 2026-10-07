package com.luminous.api.upload;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 进程中断后清理超时且无数据库文件记录的对象，失败操作保留可查询的错误与重试次数。 */
@Component
public class UploadRecoveryScheduler {
    private static final Logger log = LoggerFactory.getLogger(UploadRecoveryScheduler.class);
    private final UploadOperationService operations;
    @Value("${app.upload.recovery-enabled:true}") private boolean enabled;
    public UploadRecoveryScheduler(UploadOperationService operations) { this.operations = operations; }
    @Scheduled(fixedDelayString="${app.upload.recovery-delay-ms:60000}")
    public void scheduled() { if (enabled) { recover(); } }
    public void recover() {
        for (Long id : operations.due()) {
            try { operations.cleanup(id); }
            catch (Exception ex) { log.warn("文件补偿暂不可执行，保留待清理记录，id={}", id, ex); }
        }
    }
}
