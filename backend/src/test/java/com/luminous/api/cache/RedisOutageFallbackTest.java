package com.luminous.api.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luminous.api.post.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.net.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 另起自有 Redis 并真实停止进程，验证两种客户端同时失联后仍能受控回源。 */
@EnabledIfEnvironmentVariable(named="VOTE_TEST_REDIS_PORT", matches="[0-9]+")
class RedisOutageFallbackTest {
    @Test void stoppedRedisAndRedissonStillReturnDatabaseResult() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) { port = socket.getLocalPort(); }
        Path directory = Files.createTempDirectory(Path.of("target"), "e2e-cache-outage-");
        Path configFile = directory.resolve("redis.conf");
        Files.writeString(configFile, "bind 127.0.0.1\nport " + port + "\nsave \"\"\nappendonly no\n");
        String executable = System.getenv().getOrDefault("VOTE_TEST_REDIS_SERVER", "D:\\Redis\\redis-server.exe");
        Process server = new ProcessBuilder(executable, "redis.conf").directory(directory.toFile())
                .redirectOutput(directory.resolve("redis.stdout.log").toFile())
                .redirectError(directory.resolve("redis.stderr.log").toFile()).start();
        LettuceConnectionFactory factory = null; RedissonClient redisson = null;
        try {
            boolean ready = false;
            for (int i = 0; i < 50; i++) {
                try (Socket connection = new Socket()) {
                    connection.connect(new InetSocketAddress("127.0.0.1", port), 100); ready = true; break;
                } catch (java.io.IOException ex) { Thread.sleep(100); }
            }
            assertThat(ready).isTrue();
            factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration("127.0.0.1", port),
                    LettuceClientConfiguration.builder().commandTimeout(Duration.ofMillis(300)).build());
            factory.afterPropertiesSet(); factory.start();
            StringRedisTemplate redis = new StringRedisTemplate(factory);
            Config config = new Config();
            config.useSingleServer().setAddress("redis://127.0.0.1:" + port).setTimeout(300).setConnectTimeout(300)
                    .setRetryAttempts(0).setConnectionMinimumIdleSize(1).setConnectionPoolSize(2);
            redisson = Redisson.create(config);
            redis.opsForValue().set("luminous:e2e:cache-outage", "[E2E_TEST]");
            server.destroyForcibly(); assertThat(server.waitFor(5, TimeUnit.SECONDS)).isTrue();
            RedissonClient disconnected = redisson;
            assertThatThrownBy(() -> disconnected.getBucket("luminous:e2e:cache-outage").get()).isInstanceOf(RuntimeException.class);
            PostMapper posts = mock(PostMapper.class); Post post = new Post(); post.setId(1L); post.setTitle("[E2E_TEST] 故障回源");
            when(posts.selectById(1L)).thenReturn(post);
            PostCacheService cache = new PostCacheService(redis, redisson, posts, new ObjectMapper());
            long started = System.nanoTime();
            assertThat(cache.queryWithPassThrough(1L).getTitle()).isEqualTo(post.getTitle());
            assertThat(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - started)).isLessThan(3);
            assertThat(cache.queryWithPassThrough(1L).getId()).isEqualTo(1L);
            verify(posts, times(2)).selectById(1L);
        } finally {
            if (redisson != null) { redisson.shutdown(); }
            if (factory != null) { factory.destroy(); }
            if (server.isAlive()) { server.destroyForcibly(); server.waitFor(5, TimeUnit.SECONDS); }
            Files.deleteIfExists(configFile);
        }
    }
}
