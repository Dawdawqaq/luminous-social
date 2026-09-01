package com.luminous.api.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class VoteMQProducer {
    private static final Logger log = LoggerFactory.getLogger(VoteMQProducer.class);

    private final ObjectProvider<DefaultMQProducer> producerProvider;
    private final ObjectMapper objectMapper;
    private final VoteMQConsumer voteMQConsumer;

    @Value("${app.rocketmq.vote-topic:luminous-votes-topic}")
    private String voteTopic;

    public VoteMQProducer(
            ObjectProvider<DefaultMQProducer> producerProvider,
            ObjectMapper objectMapper,
            @Lazy VoteMQConsumer voteMQConsumer
    ) {
        this.producerProvider = producerProvider;
        this.objectMapper = objectMapper;
        this.voteMQConsumer = voteMQConsumer;
    }

    /**
     * 异步将点赞操作投递至 RocketMQ 消息队列，具备本地优雅降级
     */
    public void sendVoteMessage(VoteMessage voteMessage) {
        DefaultMQProducer producer = producerProvider.getIfAvailable();
        if (producer == null) {
            log.info("RocketMQ producer not available, falling back to synchronous database save for vote: postId={}, userId={}",
                    voteMessage.postId(), voteMessage.userId());
            voteMQConsumer.persistVoteDirectly(voteMessage);
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(voteMessage);
            Message msg = new Message(voteTopic, "POST_VOTE", voteMessage.msgId(), json.getBytes(StandardCharsets.UTF_8));

            producer.send(msg, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    log.debug("Vote message sent successfully to RocketMQ: msgId={}, offsetMsgId={}",
                            voteMessage.msgId(), sendResult.getOffsetMsgId());
                }

                @Override
                public void onException(Throwable e) {
                    log.error("Failed to send vote message to RocketMQ, executing fallback: msgId={}", voteMessage.msgId(), e);
                    voteMQConsumer.persistVoteDirectly(voteMessage);
                }
            });
        } catch (Exception e) {
            log.error("Error serializing or dispatching vote message, executing fallback: msgId={}", voteMessage.msgId(), e);
            voteMQConsumer.persistVoteDirectly(voteMessage);
        }
    }
}
