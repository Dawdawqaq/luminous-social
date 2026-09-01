package com.luminous.api.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luminous.api.common.SnowflakeIdGenerator;
import com.luminous.api.post.PostMapper;
import com.luminous.api.post.PostVote;
import com.luminous.api.post.PostVoteMapper;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class VoteMQConsumer implements MessageListenerConcurrently {
    private static final Logger log = LoggerFactory.getLogger(VoteMQConsumer.class);

    private static final String DEDUP_PREFIX = "mq:dedup:vote:";

    private final PostVoteMapper postVoteMapper;
    private final PostMapper postMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public VoteMQConsumer(
            PostVoteMapper postVoteMapper,
            PostMapper postMapper,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.postVoteMapper = postVoteMapper;
        this.postMapper = postMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
        for (MessageExt msg : msgs) {
            String msgId = msg.getKeys() != null ? msg.getKeys() : msg.getMsgId();
            String dedupKey = DEDUP_PREFIX + msgId;

            try {
                // Redis SETNX 24小时幂等去重，确保 Exactly-Once 消费语义
                Boolean isNew = redisTemplate.opsForValue().setIfAbsent(dedupKey, "PROCESSING", 24, TimeUnit.HOURS);
                if (Boolean.FALSE.equals(isNew)) {
                    log.info("Duplicate vote message detected, skipping: msgId={}", msgId);
                    continue;
                }

                String body = new String(msg.getBody(), StandardCharsets.UTF_8);
                VoteMessage voteMessage = objectMapper.readValue(body, VoteMessage.class);

                persistVoteDirectly(voteMessage);

                log.debug("Successfully consumed vote message for post {} by user {}", voteMessage.postId(), voteMessage.userId());
            } catch (Exception e) {
                log.error("Failed to consume vote message, triggering retry: msgId={}", msgId, e);
                // 消费失败移除去重标记以允许 RocketMQ 梯度重试
                redisTemplate.delete(dedupKey);
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }
        }
        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
    }

    /**
     * 极简短事务落库：仅执行精确的 DML 写入，将数据库锁持有时间压缩在毫秒级内
     */
    @Transactional
    public void persistVoteDirectly(VoteMessage message) {
        if (message == null || message.postId() == null || message.userId() == null) {
            return;
        }

        PostVote existing = postVoteMapper.selectOne(new LambdaQueryWrapper<PostVote>()
                .eq(PostVote::getPostId, message.postId())
                .eq(PostVote::getUserId, message.userId()));

        if (message.value() == 0) {
            // 取消投票
            if (existing != null) {
                postVoteMapper.deleteById(existing.getId());
            }
        } else {
            // 写入或更新投票记录
            if (existing == null) {
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

        // 原子累加/累减帖子总计数
        if (message.delta() != 0) {
            postMapper.updateVotesAtomic(message.postId(), message.delta());
        }
    }
}
