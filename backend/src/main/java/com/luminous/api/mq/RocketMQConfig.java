package com.luminous.api.mq;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RocketMQ 核心基础设施配置
 * 在 app.rocketmq.enabled=true 时自动装配并启动 DefaultMQProducer 与 DefaultMQPushConsumer
 */
@Configuration
public class RocketMQConfig {

    private static final Logger log = LoggerFactory.getLogger(RocketMQConfig.class);

    @Value("${app.rocketmq.name-server:localhost:9876}")
    private String nameServer;

    @Value("${app.rocketmq.producer-group:luminous-producer-group}")
    private String producerGroup;

    @Value("${app.rocketmq.consumer-group:luminous-consumer-group}")
    private String consumerGroup;

    @Value("${app.rocketmq.notification-topic:luminous-notifications}")
    private String notificationTopic;

    @Value("${app.rocketmq.vote-topic:luminous-votes-topic}")
    private String voteTopic;

    @Value("${app.rocketmq.vote-consumer-group:luminous-vote-consumer-group}")
    private String voteConsumerGroup;

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(prefix = "app.rocketmq", name = "enabled", havingValue = "true")
    public DefaultMQProducer defaultMQProducer() throws MQClientException {
        log.info("Initializing RocketMQ DefaultMQProducer with group [{}], nameServer [{}]", producerGroup, nameServer);
        DefaultMQProducer producer = new DefaultMQProducer(producerGroup);
        producer.setNamesrvAddr(nameServer);
        producer.setRetryTimesWhenSendFailed(2);
        producer.setSendMsgTimeout(5000);
        producer.start();
        log.info("RocketMQ DefaultMQProducer started successfully");
        return producer;
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(prefix = "app.rocketmq", name = "enabled", havingValue = "true")
    public DefaultMQPushConsumer notificationPushConsumer(NotificationMQConsumer notificationMQConsumer) throws MQClientException {
        log.info("Initializing RocketMQ NotificationPushConsumer with group [{}], nameServer [{}]", consumerGroup, nameServer);
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(consumerGroup);
        consumer.setNamesrvAddr(nameServer);
        consumer.subscribe(notificationTopic, "*");
        consumer.registerMessageListener(notificationMQConsumer);
        consumer.start();
        log.info("RocketMQ NotificationPushConsumer started and subscribed to [{}]", notificationTopic);
        return consumer;
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(prefix = "app.rocketmq", name = "enabled", havingValue = "true")
    public DefaultMQPushConsumer votePushConsumer(VoteMQConsumer voteMQConsumer) throws MQClientException {
        log.info("Initializing RocketMQ VotePushConsumer with group [{}], nameServer [{}]", voteConsumerGroup, nameServer);
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(voteConsumerGroup);
        consumer.setNamesrvAddr(nameServer);
        consumer.subscribe(voteTopic, "*");
        consumer.registerMessageListener(voteMQConsumer);
        consumer.start();
        log.info("RocketMQ VotePushConsumer started and subscribed to [{}]", voteTopic);
        return consumer;
    }
}
