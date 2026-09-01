package com.luminous.api.common;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class SnowflakeIdGeneratorTest {

    @Test
    void nextId_GeneratesPositiveUniqueIds() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1, 1);
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            long id = generator.nextId();
            assertThat(id).isPositive();
            ids.add(id);
        }
        assertThat(ids).hasSize(1000);
    }

    @Test
    void nextId_ConcurrentExecution_NoDuplicates() throws InterruptedException {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(2, 1);
        int threadCount = 20;
        int idsPerThread = 500;
        Set<Long> idSet = Collections.newSetFromMap(new ConcurrentHashMap<>());
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < idsPerThread; j++) {
                        idSet.add(generator.nextId());
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertThat(idSet).hasSize(threadCount * idsPerThread);
    }
}
