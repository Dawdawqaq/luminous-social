package com.luminous.api.vote;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luminous.api.mq.ReliableEventRepository;
import com.luminous.api.mq.VoteMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/** 先持久化投票意图与事件，再允许 Redis 更新及消息发送。 */
@Service
public class VoteCommandService {
    public record Accepted(VoteMessage message, int total) {}
    private final VoteRepository votes;
    private final ReliableEventRepository events;
    private final ObjectMapper mapper;
    private final org.springframework.context.ApplicationEventPublisher publisher;
    public VoteCommandService(VoteRepository votes, ReliableEventRepository events, ObjectMapper mapper,
                              org.springframework.context.ApplicationEventPublisher publisher) {
        this.votes = votes; this.events = events; this.mapper = mapper;
        this.publisher = publisher;
    }

    @Transactional
    public Accepted accept(long postId, long userId, int value) {
        if (value < -1 || value > 1) { throw new IllegalArgumentException("投票状态无效"); }
        votes.lockPost(postId);
        VoteRepository.Clock clock = votes.initialize(postId);
        VoteRepository.State state = votes.state(postId, userId);
        int oldValue = state == null ? 0 : state.desiredValue();
        long version = Math.addExact(clock.acceptedVersion(), 1);
        int delta = value - oldValue;
        int total = Math.addExact(clock.acceptedTotal(), delta);
        VoteMessage message = new VoteMessage(postId, userId, value, delta, System.currentTimeMillis(), UUID.randomUUID().toString(), version);
        votes.accept(postId, userId, value, version, total);
        publisher.publishEvent(new com.luminous.api.growth.GrowthEvent.Liked("POST", postId, userId));
        events.insert(new ReliableEventRepository.Event(message.msgId(), "VOTE", postId, userId, version, serialize(message), "PENDING"));
        return new Accepted(message, total);
    }

    private String serialize(VoteMessage message) {
        try { return mapper.writeValueAsString(message); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("投票事件序列化失败", ex); }
    }
}
