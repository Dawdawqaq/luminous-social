package com.luminous.api.vote;

import com.luminous.api.cache.RedisLuaScriptService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 持有同一帖子行锁重建投票缓存，恢复过程与新投票不会交叉覆盖。 */
@Service
public class VoteProjectionService {
    private final VoteRepository votes;
    private final RedisLuaScriptService scripts;
    public VoteProjectionService(VoteRepository votes, RedisLuaScriptService scripts) { this.votes = votes; this.scripts = scripts; }

    @Transactional
    public boolean refresh(long postId) { return rebuild(postId); }

    // 提交回调中原事务资源尚未解绑，独立事务才能安全获取最新已提交状态。
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public boolean refreshCommitted(long postId) { return rebuild(postId); }

    private boolean rebuild(long postId) {
        votes.lockPost(postId);
        VoteRepository.Clock clock = votes.initialize(postId);
        return scripts.replaceVoteSnapshot(postId, clock.acceptedVersion(), clock.acceptedTotal(), votes.states(postId));
    }
}
