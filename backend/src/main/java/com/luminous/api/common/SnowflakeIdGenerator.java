package com.luminous.api.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 自研分布式雪花 ID 生成器 (Snowflake ID Generator)
 * <p>
 * 结构：
 * 1位符号位 (0) + 41位毫秒时间戳 (可用约 69 年) + 5位数据中心ID (0-31) + 5位工作机器ID (0-31) + 12位毫秒内序列号 (4096个/ms)
 * <p>
 * 特性：
 * 1. 支持自定义基准时间戳 (Epoch)
 * 2. 具备时钟回拨容错保护：小回拨 (<=5ms) 自旋等待，大回拨抛出异常阻断脏数据生成
 * 3. 线程安全并发控制
 */
@Component
public class SnowflakeIdGenerator {

    private static final Logger log = LoggerFactory.getLogger(SnowflakeIdGenerator.class);

    /** 基准时间戳 (2026-01-01 00:00:00 GMT+8) */
    private static final long CUSTOM_EPOCH = 1767225600000L;

    /** 机器 ID 占用的位数 (5 bits) */
    private static final long WORKER_ID_BITS = 5L;

    /** 数据中心 ID 占用的位数 (5 bits) */
    private static final long DATACENTER_ID_BITS = 5L;

    /** 最大机器 ID: 31 */
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);

    /** 最大数据中心 ID: 31 */
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);

    /** 序列号占用的位数 (12 bits) */
    private static final long SEQUENCE_BITS = 12L;

    /** 机器 ID 向左移 12 位 */
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;

    /** 数据中心 ID 向左移 17 位 (12 + 5) */
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;

    /** 时间戳向左移 22 位 (12 + 5 + 5) */
    private static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

    /** 生成序列的掩码: 4095 (0xFFF) */
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    /** 最大容忍的时钟回拨毫秒数 */
    private static final long MAX_BACKWARD_MS = 5L;

    private static SnowflakeIdGenerator instance;

    private final long workerId;
    private final long datacenterId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;

    public SnowflakeIdGenerator(
            @Value("${app.snowflake.worker-id:1}") long workerId,
            @Value("${app.snowflake.datacenter-id:1}") long datacenterId
    ) {
        if (workerId > MAX_WORKER_ID || workerId < 0) {
            throw new IllegalArgumentException(String.format("Worker Id can't be greater than %d or less than 0", MAX_WORKER_ID));
        }
        if (datacenterId > MAX_DATACENTER_ID || datacenterId < 0) {
            throw new IllegalArgumentException(String.format("Datacenter Id can't be greater than %d or less than 0", MAX_DATACENTER_ID));
        }
        this.workerId = workerId;
        this.datacenterId = datacenterId;
        instance = this;
        log.info("Initialized SnowflakeIdGenerator with workerId={}, datacenterId={}", workerId, datacenterId);
    }

    /**
     * 静态快捷获取唯一 ID
     */
    public static long getNextId() {
        if (instance != null) {
            return instance.nextId();
        }
        return System.currentTimeMillis();
    }

    /**
     * 生成下一个全局唯一 ID (64 位有符号长整型)
     */
    public synchronized long nextId() {
        long timestamp = timeGen();

        // 时钟回拨检测
        if (timestamp < lastTimestamp) {
            long offset = lastTimestamp - timestamp;
            if (offset <= MAX_BACKWARD_MS) {
                // 小幅度回拨：自旋等待至时钟追平
                try {
                    Thread.sleep(offset + 1);
                    timestamp = timeGen();
                    if (timestamp < lastTimestamp) {
                        throw new IllegalStateException(String.format("Clock moved backwards after waiting. Refusing to generate id for %d ms", lastTimestamp - timestamp));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting for clock to catch up", e);
                }
            } else {
                throw new IllegalStateException(String.format("Clock moved backwards by %d ms, refusing to generate id", offset));
            }
        }

        // 同一毫秒内生成
        if (lastTimestamp == timestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                // 毫秒内序列溢出，等待下一毫秒
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            // 新毫秒，重置序列号为 0
            sequence = 0L;
        }

        lastTimestamp = timestamp;

        // 核心位移组装算法
        return ((timestamp - CUSTOM_EPOCH) << TIMESTAMP_LEFT_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    private long tilNextMillis(long lastTimestamp) {
        long timestamp = timeGen();
        while (timestamp <= lastTimestamp) {
            timestamp = timeGen();
        }
        return timestamp;
    }

    private long timeGen() {
        return System.currentTimeMillis();
    }
}
