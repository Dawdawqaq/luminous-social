package com.luminous.api.vote;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luminous.api.common.SnowflakeIdGenerator;
import com.luminous.api.mq.ReliableEventRepository;
import com.luminous.api.mq.VoteMessage;
import com.luminous.api.post.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** 按接收与落库两组水位对账，不能用消费滞后的记录覆盖最新用户意图。 */
@Service
public class VoteReconciliationService {
    private final VoteRepository votes;
    private final ReliableEventRepository events;
    private final PostMapper posts;
    private final PostVoteMapper ballots;
    private final VoteProjectionService projection;
    private final ObjectMapper mapper;
    public VoteReconciliationService(VoteRepository votes, ReliableEventRepository events, PostMapper posts,
                                     PostVoteMapper ballots, VoteProjectionService projection, ObjectMapper mapper) {
        this.votes = votes; this.events = events; this.posts = posts; this.ballots = ballots; this.projection = projection; this.mapper = mapper;
    }

    @Transactional
    public boolean reconcile(long postId) {
        votes.lockPost(postId);
        VoteRepository.Clock clock = votes.initialize(postId);
        List<VoteRepository.State> states = votes.states(postId);
        int desiredTotal = Math.toIntExact(states.stream().mapToLong(VoteRepository.State::desiredValue).sum());
        int appliedTotal = Math.toIntExact(states.stream().mapToLong(VoteRepository.State::appliedValue).sum());
        boolean changed = false;
        if (desiredTotal != clock.acceptedTotal()) { votes.repairAcceptedTotal(postId, desiredTotal); changed = true; }
        Map<Long, PostVote> records = new HashMap<>();
        for (PostVote vote : ballots.selectList(new LambdaQueryWrapper<PostVote>().eq(PostVote::getPostId, postId))) {
            records.put(vote.getUserId(), vote);
        }
        for (VoteRepository.State state : states) {
            PostVote record = records.remove(state.userId());
            if (state.appliedValue() == 0 && record != null) { ballots.deleteById(record.getId()); changed = true; }
            else if (state.appliedValue() != 0 && record == null) {
                record = new PostVote(); record.setId(SnowflakeIdGenerator.getNextId()); record.setPostId(postId);
                record.setUserId(state.userId()); record.setValue(state.appliedValue()); ballots.insert(record); changed = true;
            } else if (record != null && record.getValue() != state.appliedValue()) {
                record.setValue(state.appliedValue()); ballots.updateById(record); changed = true;
            }
            // 投票意图存在但对应待投递事件缺失时，从当前意图补建可靠事件。
            if (state.acceptedVersion() > state.appliedVersion()
                    && !events.hasVoteEvent(postId, state.userId(), state.acceptedVersion())) {
                VoteMessage recovery = new VoteMessage(postId, state.userId(), state.desiredValue(),
                        state.desiredValue() - state.appliedValue(), System.currentTimeMillis(), UUID.randomUUID().toString(), state.acceptedVersion());
                try {
                    events.insert(new ReliableEventRepository.Event(recovery.msgId(), "VOTE", postId, state.userId(),
                            recovery.version(), mapper.writeValueAsString(recovery), "PENDING"));
                } catch (java.io.IOException ex) { throw new IllegalStateException("恢复事件序列化失败", ex); }
                changed = true;
            }
        }
        for (PostVote extra : records.values()) { ballots.deleteById(extra.getId()); changed = true; }
        Post post = posts.selectById(postId);
        if (post.getVotes() != appliedTotal) {
            posts.updateVotesAtomic(postId, appliedTotal - post.getVotes()); changed = true;
        }
        // 缓存依据已接收意图重建，总分落库依据已应用状态修复；正常积压不构成漂移。
        return projection.refresh(postId) || changed;
    }
}
