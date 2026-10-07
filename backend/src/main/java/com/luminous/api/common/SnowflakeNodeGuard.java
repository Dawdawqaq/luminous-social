package com.luminous.api.common;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/** 生产节点启动时占用雪花节点编号，发现重复配置即终止启动。 */
@Component
@Profile("prod")
public class SnowflakeNodeGuard implements SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(SnowflakeNodeGuard.class);

    private final RLock nodeLock;
    private volatile boolean running;
    private long ownerThreadId;

    public SnowflakeNodeGuard(RedissonClient redissonClient, SnowflakeIdGenerator generator) {
        nodeLock = redissonClient.getLock("luminous:lock:snowflake:node:"
                + generator.getDatacenterId() + ":" + generator.getWorkerId());
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        try {
            // 不指定固定租期，由 Redisson 看门狗在进程运行期间续租。
            if (!nodeLock.tryLock(0, TimeUnit.SECONDS)) {
                throw new IllegalStateException("雪花节点编号已被其他实例占用，请检查 worker-id 与 datacenter-id 配置");
            }
            ownerThreadId = Thread.currentThread().threadId();
            running = true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("雪花节点注册被中断", ex);
        }
    }

    @Override
    public void stop() {
        if (running) {
            running = false;
            try {
                // 生命周期关闭回调可能在另一线程执行，按启动线程编号释放锁。
                nodeLock.unlockAsync(ownerThreadId).toCompletableFuture().join();
            } catch (Exception ex) {
                log.warn("释放雪花节点锁失败，等待锁租期到期", ex);
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        // 在请求接入组件之前注册节点，并在它们停止之后释放编号。
        return Integer.MIN_VALUE;
    }
}
