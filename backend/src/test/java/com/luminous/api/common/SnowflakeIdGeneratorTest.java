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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.yaml.snakeyaml.Yaml;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.List;
import java.util.concurrent.Future;
import java.util.ArrayList;

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

    @ParameterizedTest
    @CsvSource({"-1,1", "32,1", "1,-1", "1,32"})
    void invalidNodeId_FailsFast(long workerId, long datacenterId) {
        assertThatThrownBy(() -> new SnowflakeIdGenerator(workerId, datacenterId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingNodeConfiguration_FailsAtStartup() {
        new ApplicationContextRunner().withUserConfiguration(NodeConfiguration.class)
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @Import(SnowflakeIdGenerator.class)
    static class NodeConfiguration {
    }

    @Test
    void productionNodes_ConcurrentGenerationHasNoDuplicates() throws Exception {
        // 直接读取部署配置，节点重复或越界均会使验收失败。
        Map<String, Object> compose;
        try (var reader = Files.newBufferedReader(Path.of("..", "docker-compose.prod.yml"))) {
            // 节点参数不依赖 Compose 列表覆盖行为，去掉专用标记后由 YAML 解析器展开环境锚点。
            compose = new Yaml().load(Files.readString(Path.of("..", "docker-compose.prod.yml")).replace("!override", ""));
        }
        Map<?, ?> services = (Map<?, ?>) compose.get("services");
        List<SnowflakeIdGenerator> generators = new ArrayList<>();
        Set<String> nodeIds = new HashSet<>();
        for (String name : List.of("backend", "backend-2")) {
            Map<?, ?> service = (Map<?, ?>) services.get(name);
            Map<?, ?> environment = (Map<?, ?>) service.get("environment");
            long workerId = Long.parseLong(environment.get("SNOWFLAKE_WORKER_ID").toString());
            long datacenterId = Long.parseLong(environment.get("SNOWFLAKE_DATACENTER_ID").toString());
            assertThat(nodeIds.add(datacenterId + ":" + workerId)).isTrue();
            generators.add(new SnowflakeIdGenerator(workerId, datacenterId));
        }
        Set<Long> ids = ConcurrentHashMap.newKeySet();
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> jobs = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
            for (int i = 0; i < 8; i++) {
                SnowflakeIdGenerator generator = generators.get(i % generators.size());
                jobs.add(executor.submit(() -> {
                    start.await();
                    for (int j = 0; j < 2500; j++) {
                        assertThat(ids.add(generator.nextId())).isTrue();
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> job : jobs) {
                job.get(30, java.util.concurrent.TimeUnit.SECONDS);
            }
        }
        assertThat(ids).hasSize(20000);
    }
}
