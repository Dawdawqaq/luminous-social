package com.luminous.api.vote;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luminous.api.common.SnowflakeIdGenerator;
import com.luminous.api.mq.ReliableEventRepository;
import com.luminous.api.mq.VoteMessage;
import com.luminous.api.post.PostMapper;
import com.luminous.api.post.PostVote;
import com.luminous.api.post.PostVoteMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/** MQ 与本地降级共用的独立事务入口，避免消费者自调用绕过事务代理。 */
@Service
public class VoteTransactionService {
    private static final int MAX_BATCH_SIZE = 64;
    private final VoteRepository votes;
    private final ReliableEventRepository events;
    private final PostVoteMapper postVoteMapper;
    private final PostMapper postMapper;
    private final ObjectMapper mapper;

    public VoteTransactionService(VoteRepository votes, ReliableEventRepository events,
                                  PostVoteMapper postVoteMapper, PostMapper postMapper, ObjectMapper mapper) {
        this.votes = votes;
        this.events = events;
        this.postVoteMapper = postVoteMapper;
        this.postMapper = postMapper;
        this.mapper = mapper;
    }

    public record BatchResult(int messages, int lockedPosts, int scoreUpdates, long lockAcquisitionNanos) {}

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(VoteMessage message) {
        apply(List.of(message));
    }

    /** 候选聚合入口：最多六十四条，同一帖子仅更新一次总分；默认 MQ 仍使用逐条入口。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BatchResult processBatch(List<VoteMessage> messages) {
        if (messages == null || messages.isEmpty() || messages.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("聚合批次应包含一至六十四条投票事件");
        }
        return apply(messages);
    }

    private BatchResult apply(List<VoteMessage> messages) {
        SortedMap<Long, Boolean> locked = new TreeMap<>();
        for (VoteMessage message : messages) {
            validate(message);
            locked.put(message.postId(), false);
        }
        long started = System.nanoTime();
        // 多帖子批次按照主键顺序加锁，避免不同消费线程以相反顺序获取帖子锁。
        for (Long id : locked.keySet()) {
            locked.put(id, votes.tryLockPost(id));
        }
        long lockAcquisitionNanos = System.nanoTime() - started;
        Map<Long, Integer> deltas = new HashMap<>();
        for (VoteMessage message : messages) {
            applyMessage(message, locked.get(message.postId()), deltas);
        }
        int updates = 0;
        for (Map.Entry<Long, Integer> entry : deltas.entrySet()) {
            if (entry.getValue() != 0) {
                if (postMapper.updateVotesAtomic(entry.getKey(), entry.getValue()) != 1) {
                    throw new IllegalStateException("帖子总分更新失败");
                }
                updates++;
            }
        }
        return new BatchResult(messages.size(), locked.size(), updates, lockAcquisitionNanos);
    }

    private void validate(VoteMessage message) {
        if (message == null || message.postId() == null || message.userId() == null || message.msgId() == null
                || message.version() <= 0 || message.value() < -1 || message.value() > 1) {
            throw new IllegalArgumentException("投票事件缺少身份、有效状态或业务版本");
        }
    }

    private void applyMessage(VoteMessage message, boolean postExists, Map<Long, Integer> deltas) {
        validateDurableEvent(message);
        if (!postExists) {
            if (events.recordConsumption("VOTE", message.msgId())) {
                events.consumed(message.msgId());
            }
            return;
        }
        if (!events.recordConsumption("VOTE", message.msgId())) {
            events.consumed(message.msgId());
            return;
        }
        VoteRepository.State state = votes.state(message.postId(), message.userId());
        if (state == null || message.version() > state.acceptedVersion()) {
            throw new IllegalStateException("投票接收水位异常");
        }
        // 重复和旧版本只登记消费；只计算最新意图与已应用状态之差，不能直接相加消息 delta。
        if (message.version() == state.acceptedVersion() && message.version() > state.appliedVersion()) {
            applyBallot(message);
            deltas.merge(message.postId(), message.value() - state.appliedValue(), Math::addExact);
            votes.applied(message.postId(), message.userId(), message.value(), message.version());
        }
        events.consumed(message.msgId());
    }

    private void validateDurableEvent(VoteMessage message) {
        ReliableEventRepository.Event durable = events.find(message.msgId());
        if (durable == null || !"VOTE".equals(durable.eventType())) {
            throw new IllegalArgumentException("投票事件没有持久化接收记录");
        }
        try {
            if (!mapper.readValue(durable.payload(), VoteMessage.class).equals(message)) {
                throw new IllegalArgumentException("投票消息与持久化事件不一致");
            }
        } catch (IOException ex) {
            throw new IllegalStateException("投票事件解析失败", ex);
        }
    }

    private void applyBallot(VoteMessage message) {
        PostVote existing = postVoteMapper.selectOne(new LambdaQueryWrapper<PostVote>()
                .eq(PostVote::getPostId, message.postId()).eq(PostVote::getUserId, message.userId()));
        if (message.value() == 0) {
            if (existing != null) {
                postVoteMapper.deleteById(existing.getId());
            }
        } else if (existing == null) {
            PostVote record = new PostVote();
            record.setId(SnowflakeIdGenerator.getNextId());
            record.setPostId(message.postId());
            record.setUserId(message.userId());
            record.setValue(message.value());
            postVoteMapper.insert(record);
        } else {
            existing.setValue(message.value());
            postVoteMapper.updateById(existing);
        }
    }
}
