package com.luminous.api.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 事务提交后执行缓存等可重建副作用；回调失败不能改变已经提交的业务结果。 */
public final class AfterCommit {
    private static final Logger log = LoggerFactory.getLogger(AfterCommit.class);
    private AfterCommit() {}

    public static void run(Runnable action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { execute(action); }
            });
        } else { execute(action); }
    }

    private static void execute(Runnable action) {
        try { action.run(); }
        catch (Exception ex) { log.warn("提交后的可重建副作用失败，等待缓存过期或事件重投", ex); }
    }
}
