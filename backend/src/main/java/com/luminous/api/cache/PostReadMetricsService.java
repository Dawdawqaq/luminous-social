package com.luminous.api.cache;

import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 逐键 PFCOUNT 在同一 Pipeline 中发送；多键 PFCOUNT 会统计并集，不能用于逐帖 UV。 */
@Service
public class PostReadMetricsService {
    private static final Logger log = LoggerFactory.getLogger(PostReadMetricsService.class);
    private final StringRedisTemplate redis;

    public PostReadMetricsService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public Map<Long, Long> uvCounts(List<Long> postIds) {
        List<Long> ids = postIds.stream().distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> counts = new HashMap<>();
        try {
            List<Object> replies = redis.executePipelined((RedisCallback<Object>) connection -> {
                for (Long id : ids) {
                    connection.hyperLogLogCommands().pfCount(
                            (CacheConstants.POST_UV_KEY + id).getBytes(StandardCharsets.UTF_8));
                }
                return null;
            });
            if (replies.size() != ids.size()) {
                throw new IllegalStateException("UV 批量响应数量不匹配");
            }
            for (int index = 0; index < ids.size(); index++) {
                counts.put(ids.get(index), ((Number) replies.get(index)).longValue());
            }
        } catch (Exception ex) {
            counts.clear();
            log.warn("批量 UV 暂不可读，本页使用零作为展示回退，数量={}", ids.size(), ex);
        }
        return counts;
    }
}
