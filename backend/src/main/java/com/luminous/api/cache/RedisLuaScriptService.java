package com.luminous.api.cache;

import com.luminous.api.vote.VoteRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

/** Redis 仅保存可重建的投票投影，不能在缓存缺失时推断旧投票。 */
@Service
public class RedisLuaScriptService {
    private static final Logger log = LoggerFactory.getLogger(RedisLuaScriptService.class);
    private final StringRedisTemplate redis;
    private final ResourceLoader resources;
    private DefaultRedisScript<Long> voteScript;
    private volatile long nextAttemptAt;
    public RedisLuaScriptService(StringRedisTemplate redis, ResourceLoader resources) { this.redis = redis; this.resources = resources; }

    @PostConstruct
    public void init() {
        voteScript = new DefaultRedisScript<>();
        voteScript.setLocation(resources.getResource("classpath:scripts/vote_post.lua"));
        voteScript.setResultType(Long.class);
    }

    public boolean replaceVoteSnapshot(long postId, long version, int total, List<VoteRepository.State> states) {
        if (System.currentTimeMillis() < nextAttemptAt) { return false; }
        List<String> args = new ArrayList<>();
        args.add(String.valueOf(total)); args.add(String.valueOf(version));
        for (VoteRepository.State state : states) {
            args.add(String.valueOf(state.userId())); args.add(String.valueOf(state.desiredValue()));
        }
        try {
            Long changed = redis.execute(voteScript, List.of("luminous:vote:state:post:" + postId,
                    "luminous:stat:post:votes:" + postId, "luminous:vote:version:post:" + postId), args.toArray());
            nextAttemptAt = 0;
            return changed != null && changed == 1L;
        } catch (Exception ex) {
            // 失败后短暂跳过缓存投影，减少 Redis 故障期间持有数据库锁的时间。
            nextAttemptAt = System.currentTimeMillis() + 5000;
            log.warn("投票缓存更新失败，已持久化的投票继续由数据库和重投任务保证恢复，postId={}", postId, ex);
            return false;
        }
    }
}
