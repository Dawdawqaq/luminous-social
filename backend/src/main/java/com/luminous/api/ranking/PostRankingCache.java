package com.luminous.api.ranking;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luminous.api.common.BusinessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/** 多实例共享候选与短期阅读快照；只缓存派生数据，不承接业务事实写入。 */
@Component
public class PostRankingCache {
    public record Batch(String version, LocalDateTime generatedAt, List<PostRankingPolicy.Candidate> candidates) {
        public Batch(LocalDateTime generatedAt, List<PostRankingPolicy.Candidate> candidates) {
            this(java.util.UUID.randomUUID().toString(), generatedAt, candidates);
        }
    }
    public record Snapshot(long viewerId, String type, String period, String community,
                           long generatedAt, long expiresAt, List<Long> ids) {}
    public static final Duration SNAPSHOT_TTL = Duration.ofMinutes(10);
    private static final String PREFIX = "luminous:post-ranking:v1:";
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public PostRankingCache(StringRedisTemplate redis, ObjectMapper mapper) { this.redis = redis; this.mapper = mapper; }

    public String candidateKey(Long communityId) { return PREFIX + "candidates:" + (communityId == null ? "all" : communityId); }
    public Batch batch(Long communityId) { return read(candidateKey(communityId), Batch.class); }
    public void saveBatch(Long communityId, Batch batch) { write(candidateKey(communityId), batch, Duration.ofMinutes(3)); }
    public Snapshot snapshot(String id) { return read(PREFIX + "snapshot:" + id, Snapshot.class); }
    public void saveSnapshot(String id, Snapshot snapshot) { write(PREFIX + "snapshot:" + id, snapshot, SNAPSHOT_TTL); }

    private <T> T read(String key, Class<T> type) {
        try {
            String value = redis.opsForValue().get(key);
            return value == null ? null : mapper.readValue(value, type);
        } catch (RuntimeException | JsonProcessingException exception) { throw unavailable(); }
    }

    private void write(String key, Object value, Duration ttl) {
        try { redis.opsForValue().set(key, mapper.writeValueAsString(value), ttl); }
        catch (RuntimeException | JsonProcessingException exception) { throw unavailable(); }
    }

    public static BusinessException unavailable() {
        return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "RANKING_UNAVAILABLE", "榜单暂时无法加载，请稍后重试");
    }
}
