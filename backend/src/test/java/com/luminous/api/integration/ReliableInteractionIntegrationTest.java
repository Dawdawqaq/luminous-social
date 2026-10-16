package com.luminous.api.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luminous.api.cache.RedisLuaScriptService;
import com.luminous.api.mq.*;
import com.luminous.api.notification.NotificationEvent;
import com.luminous.api.notification.NotificationInboxRepository;
import com.luminous.api.notification.Notification;
import com.luminous.api.post.PostMapper;
import com.luminous.api.vote.*;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import java.util.List;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 真实数据库事务与 Redis 下的故障注入，避免仅用 Mock 推断回滚和业务幂等。 */
class ReliableInteractionIntegrationTest extends AbstractVoteIntegrationTest {
    @Autowired private VoteCommandService commands;
    @Autowired private VoteTransactionService transactions;
    @Autowired private VoteProjectionService projection;
    @Autowired private VoteReconciliationService reconciliation;
    @Autowired private VoteMQConsumer voteConsumer;
    @Autowired private VoteMQProducer voteSender;
    @Autowired private NotificationMQProducer notificationSender;
    @Autowired private NotificationMQConsumer notificationConsumer;
    @Autowired private NotificationTransactionService notificationTransactions;
    @Autowired private ObjectMapper mapper;
    @MockitoSpyBean private PostMapper posts;
    @MockitoSpyBean private NotificationInboxRepository notificationInbox;
    @MockitoSpyBean private ReliableEventRepository events;
    @MockitoSpyBean private RedisLuaScriptService scripts;

    private MessageExt voteMessage(VoteMessage event) throws Exception {
        MessageExt message = new MessageExt(); message.setMsgId("[E2E_TEST]-broker-" + event.msgId());
        message.setKeys(event.msgId()); message.setBody(mapper.writeValueAsBytes(event)); return message;
    }
    private MessageExt notificationMessage(NotificationEvent event, int retries) throws Exception {
        MessageExt message = new MessageExt(); message.setMsgId("[E2E_TEST]-broker-" + event.eventId());
        message.setKeys(event.eventId()); message.setBody(mapper.writeValueAsBytes(event)); message.setReconsumeTimes(retries); return message;
    }
    private long count(String table, long postId) {
        if (!List.of("post_votes", "vote_states").contains(table)) { throw new IllegalArgumentException("测试表无效"); }
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE post_id=?", Long.class, postId);
    }
    private int score(long postId) { return jdbcTemplate.queryForObject("SELECT votes FROM posts WHERE id=?", Integer.class, postId); }
    private long consumed(String eventId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM consumed_events WHERE event_id=?", Long.class, eventId);
    }

    @Test void secondDatabaseUpdateFailure_RollsBackBallotAndConsumption_ThenRetrySucceeds() throws Exception {
        long postId = createPost(true);
        VoteMessage event = commands.accept(postId, voter.id(), 1).message();
        doThrow(new IllegalStateException("[E2E_TEST] 第二次数据库更新失败")).when(posts).updateVotesAtomic(postId, 1);
        assertThat(voteConsumer.consumeMessage(List.of(voteMessage(event)), null)).isEqualTo(ConsumeConcurrentlyStatus.RECONSUME_LATER);
        assertThat(count("post_votes", postId)).isZero();
        assertThat(score(postId)).isZero(); assertThat(consumed(event.msgId())).isZero();
        assertThat(events.find(event.msgId()).status()).isEqualTo("PENDING");
        reset(posts);
        assertThat(voteConsumer.consumeMessage(List.of(voteMessage(event)), null)).isEqualTo(ConsumeConcurrentlyStatus.CONSUME_SUCCESS);
        assertThat(voteConsumer.consumeMessage(List.of(voteMessage(event)), null)).isEqualTo(ConsumeConcurrentlyStatus.CONSUME_SUCCESS);
        assertThat(count("post_votes", postId)).isEqualTo(1); assertThat(score(postId)).isEqualTo(1);
        assertThat(consumed(event.msgId())).isEqualTo(1);
    }

    @Test void crashBeforeConsumptionCommit_RollsBackAllBusinessEffects() {
        long postId = createPost(true);
        VoteMessage event = commands.accept(postId, voter.id(), 1).message();
        doThrow(new IllegalStateException("[E2E_TEST] 提交消费记录前中断")).when(events).consumed(event.msgId());
        assertThatThrownBy(() -> transactions.process(event)).isInstanceOf(IllegalStateException.class);
        assertThat(count("post_votes", postId)).isZero(); assertThat(score(postId)).isZero(); assertThat(consumed(event.msgId())).isZero();
        reset(events);
        transactions.process(event);
        assertThat(score(postId)).isEqualTo(1); assertThat(consumed(event.msgId())).isEqualTo(1);
    }

    @Test void cancelArrivesBeforeLike_OldMessageCannotResurrectVote() throws Exception {
        long postId = createPost(true);
        VoteMessage like = commands.accept(postId, voter.id(), 1).message();
        VoteMessage cancel = commands.accept(postId, voter.id(), 0).message();
        projection.refresh(postId);
        voteConsumer.consumeMessage(List.of(voteMessage(cancel)), null);
        voteConsumer.consumeMessage(List.of(voteMessage(like)), null);
        assertThat(score(postId)).isZero(); assertThat(count("post_votes", postId)).isZero();
        assertThat(consumed(like.msgId())).isEqualTo(1); assertThat(consumed(cancel.msgId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT applied_version FROM vote_states WHERE post_id=? AND user_id=?", Long.class, postId, voter.id()))
                .isEqualTo(cancel.version());
    }

    @Test void concurrentRedelivery_OnlyOneBusinessEffect() throws Exception {
        long postId = createPost(true);
        VoteMessage event = commands.accept(postId, voter.id(), 1).message();
        try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
            List<Future<?>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < 8; i++) { futures.add(executor.submit(() -> transactions.process(event))); }
            for (Future<?> future : futures) { future.get(15, TimeUnit.SECONDS); }
        }
        assertThat(score(postId)).isEqualTo(1); assertThat(consumed(event.msgId())).isEqualTo(1);
        assertThat(count("post_votes", postId)).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(booleans={true,false})
    void brokerReceivesButClientTimesOut_FallbackAndNormalConsumerRemainIdempotent(boolean brokerConsumesFirst) throws Exception {
        long postId = createPost(true);
        VoteMessage event = commands.accept(postId, voter.id(), 1).message();
        DefaultMQProducer broker = mock(DefaultMQProducer.class);
        @SuppressWarnings("unchecked") ObjectProvider<DefaultMQProducer> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(broker);
        doAnswer(invocation -> {
            if (brokerConsumesFirst) { voteConsumer.consumeMessage(List.of(voteMessage(event)), null); }
            invocation.<SendCallback>getArgument(1).onException(new TimeoutException("[E2E_TEST] Broker 已收到，客户端超时"));
            if (!brokerConsumesFirst) { voteConsumer.consumeMessage(List.of(voteMessage(event)), null); }
            return null;
        }).when(broker).send(any(org.apache.rocketmq.common.message.Message.class), any(SendCallback.class));
        VoteMQProducer sender = new VoteMQProducer(provider, mapper, transactions, projection, events);
        org.springframework.test.util.ReflectionTestUtils.setField(sender, "topic", "e2e-votes");
        sender.sendVoteMessage(event);
        verify(broker).send(any(org.apache.rocketmq.common.message.Message.class), any(SendCallback.class));
        assertThat(score(postId)).isEqualTo(1); assertThat(count("post_votes", postId)).isEqualTo(1);
        assertThat(consumed(event.msgId())).isEqualTo(1);
    }

    @Test void processStopsAfterRedisBeforePublish_NewDispatcherRecoversFromDatabase() {
        long postId = createPost(true);
        VoteMessage event = commands.accept(postId, voter.id(), 1).message();
        projection.refresh(postId);
        assertThat(score(postId)).isZero(); assertThat(events.find(event.msgId()).status()).isEqualTo("PENDING");
        new ReliableOutboxDispatcher(events, voteSender, notificationSender, mapper).retryPending();
        assertThat(score(postId)).isEqualTo(1); assertThat(events.find(event.msgId()).status()).isEqualTo("CONSUMED");
    }

    @Test void redisLossAndOutage_DoNotDoubleCountAndCancellationRemainsCorrect() {
        long postId = createPost(true);
        postService.votePost(postId, 1, voter);
        redisTemplate.delete(List.of("luminous:vote:state:post:"+postId,"luminous:vote:version:post:"+postId,"luminous:stat:post:votes:"+postId));
        postService.votePost(postId, 1, voter);
        assertThat(score(postId)).isEqualTo(1);
        doThrow(new IllegalStateException("[E2E_TEST] Redis 不可用")).when(scripts).replaceVoteSnapshot(eq(postId),anyLong(),anyInt(),anyList());
        postService.votePost(postId, -1, voter);
        postService.votePost(postId, 0, voter);
        assertThat(score(postId)).isZero(); assertThat(count("post_votes",postId)).isZero();
        reset(scripts); projection.refresh(postId);
        assertThat(redisTemplate.opsForValue().get("luminous:stat:post:votes:"+postId)).isEqualTo("0");
        assertThat(redisTemplate.opsForHash().get("luminous:vote:state:post:"+postId,String.valueOf(voter.id()))).isEqualTo("0");
    }

    @Test void reconciliationPreservesBacklog_RepairsDriftAndRebuildsMissingEvent() {
        long postId = createPost(true);
        VoteMessage event = commands.accept(postId,voter.id(),1).message();
        projection.refresh(postId);
        assertThat(reconciliation.reconcile(postId)).isFalse();
        assertThat(score(postId)).isZero();
        assertThat(redisTemplate.opsForValue().get("luminous:stat:post:votes:"+postId)).isEqualTo("1");
        jdbcTemplate.update("DELETE FROM reliable_events WHERE event_id=?",event.msgId());
        jdbcTemplate.update("UPDATE posts SET votes=99 WHERE id=?",postId);
        redisTemplate.opsForValue().set("luminous:stat:post:votes:"+postId,"99");
        redisTemplate.opsForHash().put("luminous:vote:state:post:"+postId,String.valueOf(voter.id()),"-1");
        assertThat(reconciliation.reconcile(postId)).isTrue();
        assertThat(score(postId)).isZero();
        new ReliableOutboxDispatcher(events,voteSender,notificationSender,mapper).retryPending();
        assertThat(score(postId)).isEqualTo(1);
        jdbcTemplate.update("DELETE FROM post_votes WHERE post_id=?",postId);
        jdbcTemplate.update("UPDATE posts SET votes=-50 WHERE id=?",postId);
        assertThat(reconciliation.reconcile(postId)).isTrue();
        assertThat(score(postId)).isEqualTo(1); assertThat(count("post_votes",postId)).isEqualTo(1);
        assertThat(redisTemplate.opsForValue().get("luminous:stat:post:votes:"+postId)).isEqualTo("1");
    }

    @Test void notificationInsertFailure_RollsBackConsumptionAndDebounce_ThenRedeliveryCompletes() throws Exception {
        long postId=createPost(true);
        NotificationEvent event=new NotificationEvent(voter.id(),author.id(),"LIKE_POST","POST",postId,"[E2E_TEST] 通知");
        doThrow(new IllegalStateException("[E2E_TEST] 插入失败")).when(notificationInbox).insertBatch(anyList());
        assertThat(notificationConsumer.consumeMessage(List.of(notificationMessage(event,0)),null)).isEqualTo(ConsumeConcurrentlyStatus.RECONSUME_LATER);
        assertThat(consumed(event.eventId())).isZero();
        reset(notificationInbox);
        notificationConsumer.consumeMessage(List.of(notificationMessage(event,1)),null);
        notificationConsumer.consumeMessage(List.of(notificationMessage(event,2)),null);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notifications WHERE receiver_id=?",Long.class,author.id())).isEqualTo(1);
        assertThat(consumed(event.eventId())).isEqualTo(1);
    }

    @Test void persistentNotificationFailure_IsQueryableAndCanBeReplayed() throws Exception {
        long postId=createPost(true);
        NotificationEvent event=new NotificationEvent(voter.id(),author.id(),"COMMENT_POST","POST",postId,"[E2E_TEST] 失败通知");
        doThrow(new IllegalStateException("[E2E_TEST] 持续失败")).when(notificationInbox).insertBatch(anyList());
        assertThat(notificationConsumer.consumeMessage(List.of(notificationMessage(event,3)),null)).isEqualTo(ConsumeConcurrentlyStatus.CONSUME_SUCCESS);
        long failureId=jdbcTemplate.queryForObject("SELECT id FROM failed_messages WHERE event_id=?",Long.class,event.eventId());
        assertStatus("/api/admin/mq/failures",HttpMethod.GET,null,voterToken,HttpStatus.FORBIDDEN);
        assertStatus("/api/admin/mq/failures",HttpMethod.GET,null,adminToken,HttpStatus.OK);
        assertStatus("/api/admin/mq/failures/"+failureId+"/replay",HttpMethod.POST,null,adminToken,HttpStatus.CONFLICT);
        reset(notificationInbox);
        assertStatus("/api/admin/mq/failures/"+failureId+"/replay",HttpMethod.POST,null,adminToken,HttpStatus.OK);
        assertStatus("/api/admin/mq/failures/"+failureId+"/replay",HttpMethod.POST,null,adminToken,HttpStatus.OK);
        assertThat(consumed(event.eventId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM failed_messages WHERE id=?",String.class,failureId)).isEqualTo("REPLAYED");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notifications WHERE receiver_id=?",Long.class,author.id())).isEqualTo(1);
    }
}
