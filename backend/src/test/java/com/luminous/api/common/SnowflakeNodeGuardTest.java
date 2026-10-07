package com.luminous.api.common;

import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class SnowflakeNodeGuardTest {
    @Test
    void duplicateNode_RejectsStartup() throws InterruptedException {
        RedissonClient client = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        when(client.getLock("luminous:lock:snowflake:node:1:2")).thenReturn(lock);
        when(lock.tryLock(0, TimeUnit.SECONDS)).thenReturn(false);
        SnowflakeNodeGuard guard = new SnowflakeNodeGuard(client, new SnowflakeIdGenerator(2, 1));

        assertThatThrownBy(guard::start).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已被其他实例占用");
        assertThat(guard.isRunning()).isFalse();
    }
}
