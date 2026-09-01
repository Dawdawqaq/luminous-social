package com.luminous.api.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;

@Service
public class RedisLuaScriptService {
    private static final Logger log = LoggerFactory.getLogger(RedisLuaScriptService.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final ResourceLoader resourceLoader;
    private DefaultRedisScript<List> voteScript;

    public RedisLuaScriptService(StringRedisTemplate stringRedisTemplate, ResourceLoader resourceLoader) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void init() {
        voteScript = new DefaultRedisScript<>();
        voteScript.setLocation(resourceLoader.getResource("classpath:scripts/vote_post.lua"));
        voteScript.setResultType(List.class);
    }

    @SuppressWarnings("unchecked")
    public List<Long> executeVoteScript(Long postId, Long userId, int value) {
        try {
            String recordKey = "vote:post:" + postId + ":" + userId;
            String countKey = "stat:post:votes:" + postId;
            
            return (List<Long>) stringRedisTemplate.execute(
                voteScript,
                List.of(recordKey, countKey),
                String.valueOf(value)
            );
        } catch (Exception e) {
            log.warn("Failed to execute vote Lua script for post {} and user {}", postId, userId, e);
            return null;
        }
    }
}
