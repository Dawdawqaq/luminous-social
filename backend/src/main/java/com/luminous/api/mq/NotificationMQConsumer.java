package com.luminous.api.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luminous.api.notification.NotificationEvent;
import com.luminous.api.notification.NotificationService;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * 消息通知 RocketMQ 消费者
 * 具备 Redis 幂等去重 (At-most-once / Exactly-once processing) 与异常重试机制
 */
@Component
public class NotificationMQConsumer implements MessageListenerConcurrently {

    private static final Logger log = LoggerFactory.getLogger(NotificationMQConsumer.class);
    private static final String DEDUP_PREFIX = "mq:dedup:notification:";

    private final NotificationService notificationService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public NotificationMQConsumer(
            NotificationService notificationService,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.notificationService = notificationService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
        if (msgs == null || msgs.isEmpty()) {
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        }

        for (MessageExt msg : msgs) {
            String msgId = msg.getMsgId();
            String dedupKey = DEDUP_PREFIX + msgId;

            try {
                // 1. Redis 消息去重校验 (TTL 24小时)
                Boolean isFirst = redisTemplate.opsForValue().setIfAbsent(dedupKey, "PROCESSING", Duration.ofHours(24));
                if (Boolean.FALSE.equals(isFirst)) {
                    log.info("Duplicate notification message detected and skipped: msgId={}, key={}", msgId, msg.getKeys());
                    continue;
                }

                // 2. 反序列化通知事件
                String json = new String(msg.getBody(), StandardCharsets.UTF_8);
                NotificationEvent event = objectMapper.readValue(json, NotificationEvent.class);

                // 3. 执行业务持久化
                notificationService.handleEvent(event);
                log.info("Successfully consumed notification message: msgId={}, type={}", msgId, event.type());

            } catch (Exception e) {
                log.error("Failed to process notification message: msgId={}, retryTimes={}", msgId, msg.getReconsumeTimes(), e);
                // 删除去重标记以便重试
                try {
                    redisTemplate.delete(dedupKey);
                } catch (Exception ignored) {}

                // 重试超过 3 次则丢弃或进入死信队列，避免阻塞消费
                if (msg.getReconsumeTimes() >= 3) {
                    log.error("Message reconsume exceeded max retry times (3), skipping: msgId={}", msgId);
                    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                }
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }
        }

        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
    }
}
