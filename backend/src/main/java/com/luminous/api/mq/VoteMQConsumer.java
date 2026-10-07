package com.luminous.api.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luminous.api.vote.VoteTransactionService;
import org.apache.rocketmq.client.consumer.listener.*;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.List;

/** 消费者只解析协议并调用独立事务服务，禁止通过 Redis 标记提前确认消费。 */
@Service
public class VoteMQConsumer implements MessageListenerConcurrently {
    private static final Logger log = LoggerFactory.getLogger(VoteMQConsumer.class);
    private final VoteTransactionService transactions;
    private final ObjectMapper mapper;
    public VoteMQConsumer(VoteTransactionService transactions, ObjectMapper mapper) { this.transactions = transactions; this.mapper = mapper; }

    @Override
    public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> messages, ConsumeConcurrentlyContext context) {
        if (messages == null) { return ConsumeConcurrentlyStatus.CONSUME_SUCCESS; }
        for (MessageExt message : messages) {
            try { transactions.process(mapper.readValue(message.getBody(), VoteMessage.class)); }
            catch (Exception ex) {
                log.error("投票消费失败，等待重新投递，brokerId={}", message.getMsgId(), ex);
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }
        }
        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
    }
}
