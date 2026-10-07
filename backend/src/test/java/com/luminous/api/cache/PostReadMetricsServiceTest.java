package com.luminous.api.cache;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisHyperLogLogCommands;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;

class PostReadMetricsServiceTest {
    @Test void fiftyPostsUseOnePipelineWithoutCombiningTheirVisitors() {
        var redis=mock(StringRedisTemplate.class);var connection=mock(RedisConnection.class);var hll=mock(RedisHyperLogLogCommands.class);
        when(connection.hyperLogLogCommands()).thenReturn(hll);
        when(redis.executePipelined(any(RedisCallback.class))).thenAnswer(call->{
            ((RedisCallback<?>)call.getArgument(0)).doInRedis(connection);
            return java.util.stream.LongStream.rangeClosed(1,50).boxed().map(value->(Object)value).toList();
        });
        var ids=java.util.stream.LongStream.rangeClosed(1,50).boxed().toList();
        var counts=new PostReadMetricsService(redis).uvCounts(ids);
        assertThat(counts).hasSize(50);assertThat(counts.get(50L)).isEqualTo(50);
        verify(redis,times(1)).executePipelined(any(RedisCallback.class));
        verify(redis,never()).opsForHyperLogLog();
        verify(hll,times(50)).pfCount(any(byte[][].class));
    }
    @Test void failureDoesNotFallBackToIndividualRedisRequests() {
        var redis=mock(StringRedisTemplate.class);when(redis.executePipelined(any(RedisCallback.class))).thenThrow(new IllegalStateException("Redis 故障"));
        assertThat(new PostReadMetricsService(redis).uvCounts(List.of(1L,2L))).isEmpty();
        verify(redis,never()).opsForHyperLogLog();
    }
}
