package com.luminous.api.integration;

import com.luminous.api.common.SnowflakeIdGenerator;
import com.luminous.api.common.SnowflakeNodeGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 用两个独立客户端模拟两个生产进程，验证节点占用与释放。 */
@EnabledIfEnvironmentVariable(named = "VOTE_TEST_REDIS_PORT", matches = "\\d+")
class SnowflakeNodeGuardRedisTest {
    @Test
    void duplicateProcess_RejectsStartupAndCanStartAfterOwnerStops() throws Exception {
        int port = Integer.parseInt(System.getenv("VOTE_TEST_REDIS_PORT"));
        assertThat(port).isNotEqualTo(6379).isNotEqualTo(63790).isNotEqualTo(63797);
        Config config = new Config();
        config.useSingleServer().setAddress("redis://127.0.0.1:" + port)
                .setConnectionMinimumIdleSize(1).setConnectionPoolSize(2);
        RedissonClient firstClient = Redisson.create(config);
        RedissonClient secondClient = Redisson.create(config);
        SnowflakeNodeGuard first = new SnowflakeNodeGuard(firstClient, new SnowflakeIdGenerator(31, 31));
        SnowflakeNodeGuard second = new SnowflakeNodeGuard(secondClient, new SnowflakeIdGenerator(31, 31));
        try {
            first.start();
            assertThatThrownBy(second::start).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("已被其他实例占用");
            // 模拟关闭回调位于另一线程，验证显式所有者编号可正确释放锁。
            try (var executor = Executors.newSingleThreadExecutor()) {
                executor.submit((Runnable) first::stop).get(10, TimeUnit.SECONDS);
            }
            second.start();
            assertThat(second.isRunning()).isTrue();
        } finally {
            first.stop();
            second.stop();
            firstClient.shutdown();
            secondClient.shutdown();
        }
    }
}
