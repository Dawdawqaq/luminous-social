package com.luminous.api.lumi;

import com.luminous.api.cache.PostCacheService;
import com.luminous.api.notification.NotificationEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 真实开发 MySQL 临时库验证事务、并发和状态结算，不把模拟数据库当作锁行为证据。 */
@EnabledIfEnvironmentVariable(named="LUMINOUS_MYSQL_REVIEW_PASSWORD",matches=".+")
class LumiMySqlTest {
    private final String database="luminous_e2e_lumi_"+UUID.randomUUID().toString().replace("-","");
    private JdbcTemplate admin,jdbc;
    private TransactionTemplate tx;
    private LumiRepository store;
    private LumiTaskService tasks;
    private LumiProperties p;
    private ApplicationEventPublisher events;
    private LumiChatRepository chats;
    private LumiChatService chatService;
    private LumiChatProperties chatProperties;
    @BeforeEach void setup() throws Exception {
        String user=System.getenv().getOrDefault("LUMINOUS_MYSQL_REVIEW_USER","root"),password=System.getenv("LUMINOUS_MYSQL_REVIEW_PASSWORD");
        String base="jdbc:mysql://127.0.0.1:33067/",options="?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        admin=new JdbcTemplate(new DriverManagerDataSource(base+options,user,password));
        admin.execute("CREATE DATABASE `"+database+"` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        var source=new DriverManagerDataSource(base+database+options,user,password);jdbc=new JdbcTemplate(source);
        try(var c=source.getConnection()){
            for(String file:List.of("V1__init_schema.sql","V5__add_role_and_ban.sql","V6__add_post_status.sql"))
                ScriptUtils.executeSqlScript(c,new FileSystemResource("src/main/resources/db/migration/"+file));
            jdbc.execute("ALTER TABLE comments ADD COLUMN parent_id BIGINT,ADD COLUMN reply_to_comment_id BIGINT,ADD COLUMN reply_to_user_id BIGINT");
            ScriptUtils.executeSqlScript(c,new FileSystemResource("src/main/resources/db/migration/V28__lumi_post_assistant.sql"));
            ScriptUtils.executeSqlScript(c,new FileSystemResource("src/main/resources/db/migration/V29__lumi_private_chat.sql"));
            ScriptUtils.executeSqlScript(c,new FileSystemResource("src/main/resources/db/migration/V30__lumi_chat_stream_revision.sql"));
            ScriptUtils.executeSqlScript(c,new FileSystemResource("src/main/resources/db/migration/V31__lumi_chat_conversations.sql"));
            ScriptUtils.executeSqlScript(c,new FileSystemResource("src/main/resources/db/migration/V32__lumi_bot_internal_identity.sql"));
        }
        jdbc.update("UPDATE users SET display_name='[E2E_TEST] Lumi' WHERE id=?",LumiProperties.BOT_ID);
        for(long id=1;id<=14;id++)jdbc.update("INSERT INTO users(id,username,display_name,password_hash,join_date) VALUES(?,?,?,'[E2E_TEST]','[E2E_TEST]')",id,"E2E_TEST_"+id,"[E2E_TEST] 用户"+id);
        jdbc.update("INSERT INTO communities(id,slug,name,description,rules_json,moderators_json) VALUES(1,'e2e_test','[E2E_TEST]','[E2E_TEST]','[]','[]')");
        for(long id=1;id<=12;id++)jdbc.update("INSERT INTO posts(id,author_id,community_id,title,content,status) VALUES(?,1,1,'[E2E_TEST]','[E2E_TEST] 正文','NORMAL')",id);
        tx=new TransactionTemplate(new DataSourceTransactionManager(source));store=new LumiRepository(sourceTemplate(source));p=new LumiProperties();p.setEnabled(true);p.setApiKey("[E2E_TEST] no outbound calls");
        events=mock(ApplicationEventPublisher.class);tasks=new LumiTaskService(store,p,events,mock(PostCacheService.class));
        chats=new LumiChatRepository(jdbc);chatProperties=new LumiChatProperties();chatService=new LumiChatService(chats,store,new LumiBudgetRepository(jdbc),p,chatProperties,new LumiConversationRepository(jdbc));
    }
    private JdbcTemplate sourceTemplate(DriverManagerDataSource source){return new JdbcTemplate(source);}
    private long sequence=100;
    private LumiRepository.Task request(long user,long post,String question) {
        long id=++sequence;
        return tx.execute(status->{store.lockPost(post);jdbc.update("INSERT INTO comments(id,post_id,author_id,content) VALUES(?,?,?,?)",id,post,user,question);tasks.requested(new LumiDtos.Requested(id));return store.forComment(id);});
    }
    private LumiTaskService.Work claim(){return tx.execute(status->tasks.claim());}
    private void failed(LumiTaskService.Work work){tx.executeWithoutResult(status->tasks.fail(work,"E2E_TEST","[E2E_TEST] 失败",false));}
    private void age(long user){jdbc.update("UPDATE lumi_tasks SET accepted_at=DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 61 SECOND) WHERE user_id=?",user);}
    @AfterEach void cleanup(){if(admin!=null)admin.execute("DROP DATABASE IF EXISTS `"+database+"`");}
    @Test void differentPostsWithSnapshotsBeforeControlRespectQueueCapacity() throws Exception {
        p.setQueueCapacity(1);
        var coordinated=spy(new LumiRepository(jdbc));
        var service=new LumiTaskService(coordinated,p,events,mock(PostCacheService.class));
        var beforeControl=new CyclicBarrier(2);
        doAnswer(call->{beforeControl.await(10,TimeUnit.SECONDS);return call.callRealMethod();}).when(coordinated).coordinate();
        var pool=Executors.newFixedThreadPool(2);
        try {
            List<Future<String>> futures=new ArrayList<>();
            for(long user:new long[]{1,2}) futures.add(pool.submit(()->tx.execute(status->{
                store.lockPost(user);
                long comment=900000+user;
                jdbc.update("INSERT INTO comments(id,post_id,author_id,content) VALUES(?,?,?,?)",comment,user,user,"@Lumi [E2E_TEST] 不同帖子并发准入");
                service.requested(new LumiDtos.Requested(comment));
                return store.forComment(comment).status();
            })));
            List<String> states=new ArrayList<>();
            for(var future:futures)states.add(future.get(20,TimeUnit.SECONDS));
            assertThat(states).containsExactlyInAnyOrder("PENDING","REJECTED");
            assertThat(store.allPending()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lumi_attempts",Long.class)).isZero();
        } finally {pool.shutdownNow();assertThat(pool.awaitTermination(5,TimeUnit.SECONDS)).isTrue();}
    }

    @ParameterizedTest @ValueSource(ints={2,3})
    void staleRetryCannotResurrectSuccessfulTask(int attempts) throws Exception {
        p.setMaxAttempts(attempts);
        var task=request(1,1,"@Lumi [E2E_TEST] 陈旧重试");
        failed(claim());age(1);
        var snapshotReady=new CountDownLatch(1);var release=new CountDownLatch(1);
        var pool=Executors.newSingleThreadExecutor();
        try {
            var stale=pool.submit(()->tx.execute(status->{
                assertThat(store.task(task.id()).status()).isEqualTo("FAILED");
                snapshotReady.countDown();awaitGate(release);
                return tasks.retry(task.id(),1);
            }));
            assertThat(snapshotReady.await(10,TimeUnit.SECONDS)).isTrue();
            assertThat(tx.execute(status->tasks.retry(task.id(),1)).status()).isEqualTo("PENDING");
            var work=claim();
            assertThat(Boolean.TRUE.equals(tx.execute(status->tasks.publish(work,"[E2E_TEST] 唯一成功回复",List.of())))).isTrue();
            long reply=store.task(task.id()).replyId();
            release.countDown();
            var result=stale.get(20,TimeUnit.SECONDS);
            assertThat(result.status()).isEqualTo("SUCCEEDED");
            assertThat(result.replyId()).isEqualTo(""+reply);
            assertThat(claim()).isNull();
            assertThat(store.task(task.id()).status()).isEqualTo("SUCCEEDED");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comments WHERE author_id=? AND reply_to_comment_id=?",Long.class,LumiProperties.BOT_ID,task.commentId())).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lumi_attempts WHERE task_id=?",Long.class,task.id())).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT reserved_micros FROM lumi_budget_days",Long.class)).isZero();
            verify(events,times(1)).publishEvent(any(NotificationEvent.class));
        } finally {release.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(5,TimeUnit.SECONDS)).isTrue();}
    }

    private static void awaitGate(CountDownLatch gate) {
        try {if(!gate.await(15,TimeUnit.SECONDS))throw new IllegalStateException("[E2E_TEST] 等待事务栅栏超时");}
        catch(InterruptedException error){Thread.currentThread().interrupt();throw new IllegalStateException(error);}
    }

    @ParameterizedTest @CsvSource({"true,false","false,false","true,true","false,true"})
    void oldSnapshotsRespectCrossChannelQueueAndActiveUserLimits(boolean latePost,boolean sameUser) throws Exception {
        p.setQueueCapacity(sameUser?20:1);
        long earlyUser=1,lateUser=sameUser?1:2;
        var session=chatPage(latePost?earlyUser:lateUser).sessionId();
        var ready=new CountDownLatch(1);var release=new CountDownLatch(1);
        var pool=Executors.newSingleThreadExecutor();
        try {
            var late=pool.submit(()->tx.execute(status->{
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lumi_tasks",Long.class)).isZero();
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lumi_chat_turns",Long.class)).isZero();
                ready.countDown();awaitGate(release);
                if(latePost)return request(lateUser,2,"@Lumi [E2E_TEST] 旧快照帖子请求").status();
                return chatService.send(lateUser,new LumiChatDtos.Send(session,UUID.randomUUID().toString(),"[E2E_TEST] 旧快照私聊请求")).status();
            }));
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();
            if(latePost)tx.execute(status->chatService.send(earlyUser,new LumiChatDtos.Send(session,UUID.randomUUID().toString(),"[E2E_TEST] 先提交私聊")));
            else request(earlyUser,1,"@Lumi [E2E_TEST] 先提交帖子请求");
            release.countDown();
            if(latePost)assertThat(late.get(20,TimeUnit.SECONDS)).isEqualTo("REJECTED");
            else assertThatThrownBy(()->late.get(20,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(com.luminous.api.common.BusinessException.class)
                    .hasRootCauseMessage(sameUser?"请等 Lumi 完成上一条回复":"Lumi 现在有点忙，请稍后重试");
            assertThat(store.allPending()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lumi_attempts",Long.class)).isZero();
        } finally {release.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(5,TimeUnit.SECONDS)).isTrue();}
    }

    @Test void simultaneousRetriesOfOneFailedTaskCreateOneNewAttemptAndReply() throws Exception {
        p.setMaxAttempts(3);
        var task=request(1,1,"@Lumi [E2E_TEST] 并发重试");failed(claim());age(1);
        var beforePost=new CyclicBarrier(2);var repository=spy(new LumiRepository(jdbc));
        doAnswer(call->{var row=call.callRealMethod();beforePost.await(10,TimeUnit.SECONDS);return row;})
                .when(repository).task(task.id());
        var service=new LumiTaskService(repository,p,events,mock(PostCacheService.class));
        var pool=Executors.newFixedThreadPool(2);
        try {
            var first=pool.submit(()->tx.execute(status->service.retry(task.id(),1)));
            var second=pool.submit(()->tx.execute(status->service.retry(task.id(),1)));
            assertThat(first.get(20,TimeUnit.SECONDS).status()).isEqualTo("PENDING");
            assertThat(second.get(20,TimeUnit.SECONDS).status()).isEqualTo("PENDING");
        } finally {pool.shutdownNow();assertThat(pool.awaitTermination(5,TimeUnit.SECONDS)).isTrue();}
        var work=claim();assertThat(Boolean.TRUE.equals(tx.execute(status->tasks.publish(work,"[E2E_TEST] 只发布一次",List.of())))).isTrue();
        assertThat(claim()).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lumi_attempts WHERE task_id=?",Long.class,task.id())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comments WHERE author_id=? AND reply_to_comment_id=?",Long.class,LumiProperties.BOT_ID,task.commentId())).isEqualTo(1);
        verify(events,times(1)).publishEvent(any(NotificationEvent.class));
    }

    @Test void conditionalRequeueRejectsStaleStateAndLeaseWithoutChangingSuccessfulReply() {
        var task=request(1,1,"@Lumi [E2E_TEST] 重入队状态保护");failed(claim());age(1);
        var stale=store.task(task.id());
        var altered=new LumiRepository.Task(stale.id(),stale.commentId(),stale.postId(),stale.userId(),stale.day(),
                stale.status(),stale.acceptedAt(),"[E2E_TEST] 不同租约",stale.attempts(),stale.replyId(),stale.error(),stale.message());
        assertThat(Boolean.TRUE.equals(tx.execute(status->{store.coordinate();return store.requeue(altered,store.now());}))).isFalse();
        assertThat(store.task(task.id()).status()).isEqualTo("FAILED");
        tx.execute(status->tasks.retry(task.id(),1));var work=claim();
        tx.executeWithoutResult(status->tasks.publish(work,"[E2E_TEST] 保留成功回复",List.of()));
        var successful=store.task(task.id());
        assertThat(Boolean.TRUE.equals(tx.execute(status->{store.coordinate();return store.requeue(stale,store.now());}))).isFalse();
        assertThat(store.task(task.id())).isEqualTo(successful);
    }

    @Test void stalePrivateChatRetryKeepsSuccessfulTurnAndAccounting() throws Exception {
        p.setMaxAttempts(3);
        var turn=chatSend(1,"[E2E_TEST] 私聊陈旧重试");var work=chatClaim();
        tx.executeWithoutResult(status->chatService.fail(work,"[E2E_TEST] 首次失败"));chatAge(1);
        long id=Long.parseLong(turn.id());
        var ready=new CountDownLatch(1);var release=new CountDownLatch(1);var pool=Executors.newSingleThreadExecutor();
        try {
            var late=pool.submit(()->tx.execute(status->{
                assertThat(chats.turn(id).status()).isEqualTo("FAILED");ready.countDown();awaitGate(release);
                return chatService.retry(id,1);
            }));
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();
            tx.execute(status->chatService.retry(id,1));chatComplete(chatClaim());
            var completed=chats.turn(id);release.countDown();
            assertThat(late.get(20,TimeUnit.SECONDS).status()).isEqualTo("SUCCEEDED");
            assertThat(chats.turn(id)).isEqualTo(completed);
            assertThat(chatClaim()).isNull();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lumi_attempts WHERE task_id=?",Long.class,id)).isEqualTo(2);
        } finally {release.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(5,TimeUnit.SECONDS)).isTrue();}
    }

    @ParameterizedTest @ValueSource(strings={"cooldown","user","userPost","post"})
    void oldSnapshotAdmissionReadsCurrentCooldownAndDailyLimits(String limit) throws Exception {
        if(limit.equals("user"))p.setUserDaily(1);
        if(limit.equals("userPost"))p.setUserPostDaily(1);
        if(limit.equals("post"))p.setPostDaily(1);
        var ready=new CountDownLatch(1);var release=new CountDownLatch(1);var pool=Executors.newSingleThreadExecutor();
        try {
            var late=pool.submit(()->tx.execute(status->{
                assertThat(store.count("1=1")).isZero();ready.countDown();awaitGate(release);
                return request(limit.equals("post")?2:1,limit.equals("user")?2:1,"@Lumi [E2E_TEST] 旧快照限额");
            }));
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();
            request(1,1,"@Lumi [E2E_TEST] 先完成请求");var work=claim();
            tx.executeWithoutResult(status->tasks.publish(work,"[E2E_TEST] 已计入额度",List.of()));
            if(!limit.equals("cooldown"))age(1);
            release.countDown();var rejected=late.get(20,TimeUnit.SECONDS);
            assertThat(rejected.status()).isEqualTo("REJECTED");
            String reason=switch(limit){case "cooldown"->"频繁";case "user"->"今天调用";case "userPost"->"今天在这篇帖子";default->"这篇帖子今天";};
            assertThat(rejected.message()).contains(reason);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lumi_attempts",Long.class)).isEqualTo(1);
            assertThat(store.allPending()).isZero();
        } finally {release.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(5,TimeUnit.SECONDS)).isTrue();}
    }

    @Test void oldSnapshotPrivateChatAdmissionRespectsDailyQuotaAfterCompletion() throws Exception {
        chatProperties.setUserDaily(1);String session=chatPage(1).sessionId();
        var ready=new CountDownLatch(1);var release=new CountDownLatch(1);var pool=Executors.newSingleThreadExecutor();
        try {
            var late=pool.submit(()->tx.execute(status->{
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lumi_chat_turns",Long.class)).isZero();
                ready.countDown();awaitGate(release);
                return chatService.send(1,new LumiChatDtos.Send(session,UUID.randomUUID().toString(),"[E2E_TEST] 私聊日额度"));
            }));
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();
            chatSend(1,"[E2E_TEST] 先完成私聊");chatComplete(chatClaim());chatAge(1);
            release.countDown();
            assertThatThrownBy(()->late.get(20,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
                    .hasRootCauseMessage("今天与 Lumi 聊天的次数已用完");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lumi_chat_turns",Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lumi_attempts",Long.class)).isEqualTo(1);
        } finally {release.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(5,TimeUnit.SECONDS)).isTrue();}
    }

    @ParameterizedTest @ValueSource(strings={"publish","fail"})
    void staleWorkerCallbackCannotOverwriteSuccessfulTerminalState(String callback) throws Exception {
        var task=request(1,1,"@Lumi [E2E_TEST] 迟到工作器回调");var work=claim();
        var ready=new CountDownLatch(1);var release=new CountDownLatch(1);var pool=Executors.newSingleThreadExecutor();
        try {
            var late=pool.submit(()->tx.execute(status->{
                assertThat(store.task(task.id()).status()).isEqualTo("RUNNING");ready.countDown();awaitGate(release);
                if(callback.equals("publish"))return tasks.publish(work,"[E2E_TEST] 迟到重复回复",List.of());
                tasks.fail(work,"E2E_TEST","[E2E_TEST] 迟到失败",false);return false;
            }));
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();
            tx.executeWithoutResult(status->tasks.publish(work,"[E2E_TEST] 成功终态",List.of()));
            var completed=store.task(task.id());release.countDown();
            assertThat(late.get(20,TimeUnit.SECONDS)).isFalse();
            assertThat(store.task(task.id())).isEqualTo(completed);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comments WHERE author_id=? AND reply_to_comment_id=?",Long.class,LumiProperties.BOT_ID,task.commentId())).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lumi_attempts WHERE task_id=?",Long.class,task.id())).isEqualTo(1);
            verify(events,times(1)).publishEvent(any(NotificationEvent.class));
        } finally {release.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(5,TimeUnit.SECONDS)).isTrue();}
    }

    @Test void oldSnapshotSharedClaimDoesNotExceedRunningConcurrencyOrReserveAgain() throws Exception {
        p.setConcurrency(1);request(1,1,"@Lumi [E2E_TEST] 帖子待执行");chatSend(2,"[E2E_TEST] 私聊待执行");
        var ready=new CountDownLatch(1);var release=new CountDownLatch(1);var pool=Executors.newSingleThreadExecutor();
        try {
            var late=pool.submit(()->tx.execute(status->{
                assertThat(store.allRunning()).isZero();ready.countDown();awaitGate(release);
                return chatService.claim();
            }));
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();
            assertThat(claim()).isNotNull();release.countDown();
            assertThat(late.get(20,TimeUnit.SECONDS)).isNull();
            assertThat(store.allRunning()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lumi_attempts",Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT calls FROM lumi_budget_days",Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT reserved_micros FROM lumi_budget_days",Long.class)).isEqualTo(p.reservation());
        } finally {release.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(5,TimeUnit.SECONDS)).isTrue();}
    }

    @Test void webAndEmptyRequestsDoNotReserveQuotaOrCallBudget() {
        assertThat(request(1,1,"@Lumi 联网查一下").status()).isEqualTo("REJECTED");
        assertThat(request(1,1,"@Lumi").status()).isEqualTo("REJECTED");
        assertThat(request(1,1,"@Lumi 解释帖子").status()).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lumi_attempts",Long.class)).isZero();
    }
    @Test void queuedAndCooldownApplyAcrossPostsAndFailureReleasesOnlySuccessQuota() {
        request(1,1,"@Lumi [E2E_TEST] 解释");
        assertThat(request(1,2,"@Lumi [E2E_TEST] 解释").message()).contains("上一条");
        failed(claim());
        assertThat(request(1,2,"@Lumi [E2E_TEST] 解释").message()).contains("频繁");
        age(1);assertThat(request(1,2,"@Lumi [E2E_TEST] 解释").status()).isEqualTo("PENDING");
    }
    @Test void sharedPostLimitAndUserPostLimitCountSuccessfulWorkAfterDeletion() {
        for(int i=0;i<2;i++){
            request(1,1,"@Lumi [E2E_TEST] 解释");var work=claim();
            tx.executeWithoutResult(status->tasks.publish(work,"[E2E_TEST] 简短回答",List.of()));age(1);
        }
        jdbc.update("DELETE FROM comments WHERE author_id=?",LumiProperties.BOT_ID);
        assertThat(request(1,1,"@Lumi [E2E_TEST] 再问").message()).contains("这篇帖子");
        for(int user=2;user<=9;user++){request(user,1,"@Lumi [E2E_TEST] 解释");var work=claim();tx.executeWithoutResult(status->tasks.publish(work,"[E2E_TEST] 回答",List.of()));}
        assertThat(request(10,1,"@Lumi [E2E_TEST] 再问").message()).contains("这篇帖子今天");
    }
    @Test void repeatedPublishHasExactlyOneReplyAndOneNotificationAndNoGrowthEvent() {
        var task=request(1,1,"@Lumi [E2E_TEST] 总结");
        tx.executeWithoutResult(status->tasks.requested(new LumiDtos.Requested(task.commentId())));
        var work=claim();assertThat(work).isNotNull();
        assertThat(Boolean.TRUE.equals(tx.execute(status->tasks.publish(work,"[E2E_TEST] 总结",List.of())))).isTrue();
        assertThat(Boolean.TRUE.equals(tx.execute(status->tasks.publish(work,"[E2E_TEST] 总结",List.of())))).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comments WHERE author_id=?",Long.class,LumiProperties.BOT_ID)).isEqualTo(1);
        verify(events,times(1)).publishEvent(any(NotificationEvent.class));verifyNoMoreInteractions(events);
    }
    @Test void botReplyUsesApplicationTimeEvenWhenDatabaseSessionHasAnotherZone() throws Exception {
        var task=request(1,1,"@Lumi [E2E_TEST] 时间回归");
        try(var connection=Objects.requireNonNull(jdbc.getDataSource()).getConnection()) {
            // 刻意让数据库会话与应用时区不同，复现默认 CURRENT_TIMESTAMP 造成的未来时间。
            String otherZone=ZoneId.systemDefault().getRules().getOffset(Instant.now()).getTotalSeconds()==0?"+08:00":"+00:00";
            try(var statement=connection.createStatement()){statement.execute("SET time_zone='"+otherZone+"'");}
            var isolated=new JdbcTemplate(new SingleConnectionDataSource(connection,true));
            var before=LocalDateTime.now().minusSeconds(1);
            new LumiRepository(isolated).insertReply(900001,task,task.commentId(),"[E2E_TEST] 时间口径一致");
            var created=isolated.queryForObject("SELECT created_at FROM comments WHERE id=900001",LocalDateTime.class);
            assertThat(created).isBetween(before,LocalDateTime.now().plusSeconds(1));
            // 数据库存储精度可能向上舍入毫秒，避开整分钟边界后验证显示口径。
            assertThat(com.luminous.api.common.TimeText.from(created.minusMinutes(55).minusSeconds(2))).isEqualTo("55分钟前");
        }
    }
    @Test void inputVisibilityIsCheckedAgainBeforeSendingAndBeforePublishing() {
        var task=request(1,1,"@Lumi [E2E_TEST] 解释");var work=claim();
        jdbc.update("UPDATE posts SET is_public=FALSE WHERE id=1");
        assertThat(Boolean.TRUE.equals(tx.execute(status->tasks.sending(work,work.attempt(),List.of())))).isFalse();
        assertThat(Boolean.TRUE.equals(tx.execute(status->tasks.publish(work,"[E2E_TEST] 回复",List.of())))).isFalse();
        assertThat(store.task(task.id()).status()).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT SUM(spent_micros) FROM lumi_budget_days",Long.class)).isZero();
    }
    @Test void deletedRootPreventsReplyEvenWhenNestedRequestSurvives() {
        jdbc.update("INSERT INTO comments(id,post_id,author_id,content) VALUES(90,1,1,'[E2E_TEST] root')");
        var task=request(2,1,"@Lumi [E2E_TEST] 解释");jdbc.update("UPDATE comments SET parent_id=90 WHERE id=?",task.commentId());var work=claim();
        jdbc.update("DELETE FROM comments WHERE id=90");
        assertThat(Boolean.TRUE.equals(tx.execute(status->tasks.publish(work,"[E2E_TEST] 回复",List.of())))).isFalse();
    }
    @Test void globalConcurrencyAndBudgetAreDurableAcrossWorkers() {
        request(1,1,"@Lumi [E2E_TEST] 解释");request(2,2,"@Lumi [E2E_TEST] 解释");request(3,3,"@Lumi [E2E_TEST] 解释");
        assertThat(claim()).isNotNull();assertThat(claim()).isNotNull();assertThat(claim()).isNull();
        jdbc.update("UPDATE lumi_tasks SET lease_until=DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 1 SECOND) WHERE status='RUNNING'");p.setDailyBudgetMicros(1);
        assertThat(claim()).isNull();
        assertThat(store.count("status='RUNNING'")).isZero();assertThat(store.count("status='FAILED'")).isEqualTo(3);
    }
    @Test void usageSettlesOnceAndUnknownStartedAttemptIsChargedConservatively() {
        request(1,1,"@Lumi [E2E_TEST] 解释");var work=claim();
        assertThat(Boolean.TRUE.equals(tx.execute(status->tasks.sending(work,work.attempt(),List.of())))).isTrue();
        failed(work);failed(work);
        assertThat(jdbc.queryForObject("SELECT spent_micros FROM lumi_budget_days",Long.class)).isEqualTo(p.reservation());
        assertThat(jdbc.queryForObject("SELECT reserved_micros FROM lumi_budget_days",Long.class)).isZero();
        request(2,2,"@Lumi [E2E_TEST] 解释");var other=claim();
        tx.executeWithoutResult(status->{tasks.usage(other.attempt(),100,30);tasks.usage(other.attempt(),100,30);});
        assertThat(jdbc.queryForObject("SELECT spent_micros FROM lumi_budget_days",Long.class)).isEqualTo(p.reservation()+p.cost(100,30));
    }
    @Test void quotaDayUsesShanghaiAndRetriesPreserveTaskIdentityAndEnforceOwnership() {
        assertThat(store.day(java.time.LocalDateTime.of(2026,10,15,16,0))).isEqualTo(java.time.LocalDate.of(2026,10,16));
        var task=request(1,1,"@Lumi [E2E_TEST] 解释");failed(claim());age(1);
        assertThat(tx.execute(status->tasks.retry(task.id(),1)).id()).isEqualTo(""+task.id());
        assertThatThrownBy(()->tx.execute(status->tasks.retry(task.id(),2))).isInstanceOf(com.luminous.api.common.BusinessException.class);
    }
    @Test void concurrentAdmissionsRespectSingleUserSlot() throws Exception {
        var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        try{
            List<Future<String>> futures=new ArrayList<>();
            for(long id:new long[]{201,202})futures.add(pool.submit(()->{gate.await();return tx.execute(status->{store.lockPost(1);jdbc.update("INSERT INTO comments(id,post_id,author_id,content) VALUES(?,1,1,'@Lumi [E2E_TEST] 并发')",id);tasks.requested(new LumiDtos.Requested(id));return store.forComment(id).status();});}));
            gate.countDown();List<String> states=new ArrayList<>();for(var future:futures)states.add(future.get(10,TimeUnit.SECONDS));
            assertThat(states).containsExactlyInAnyOrder("PENDING","REJECTED");
        }finally{pool.shutdownNow();}
    }
    @Test void userDailyLimitSpansDifferentPostsAndPreviousDayDoesNotConsumeToday() {
        for(int post=1;post<=10;post++){
            request(1,post,"@Lumi [E2E_TEST] 解释");var work=claim();
            tx.executeWithoutResult(status->tasks.publish(work,"[E2E_TEST] 回答",List.of()));age(1);
        }
        assertThat(request(1,11,"@Lumi [E2E_TEST] 解释").message()).contains("今天调用");
        jdbc.update("UPDATE lumi_tasks SET quota_day=DATE_SUB(DATE(DATE_ADD(UTC_TIMESTAMP(),INTERVAL 8 HOUR)),INTERVAL 1 DAY) WHERE user_id=1 AND status='SUCCEEDED'");
        assertThat(request(1,11,"@Lumi [E2E_TEST] 解释").status()).isEqualTo("PENDING");
    }
    @Test void secondarySourceVisibilityAndBotGrowthEligibilityAreEnforced() {
        request(1,1,"@Lumi [E2E_TEST] 解释");var work=claim();
        jdbc.update("UPDATE posts SET is_public=FALSE WHERE id=2");
        assertThat(Boolean.TRUE.equals(tx.execute(status->tasks.publish(work,"[E2E_TEST] 另一篇来源",List.of(2L))))).isFalse();
        var growth=new com.luminous.api.growth.GrowthRepository(jdbc);
        assertThat(Boolean.TRUE.equals(tx.execute(status->growth.activeUser(LumiProperties.BOT_ID)))).isFalse();
        assertThat(Boolean.TRUE.equals(tx.execute(status->growth.activeUser(1)))).isTrue();
    }
    @Test void staleWorkerTokenCannotPublishOrSettleNewOwnership() {
        request(1,1,"@Lumi [E2E_TEST] 解释");var work=claim();
        jdbc.update("UPDATE lumi_tasks SET lease_token='[E2E_TEST] newer' WHERE id=?",work.task().id());
        assertThat(Boolean.TRUE.equals(tx.execute(status->tasks.publish(work,"[E2E_TEST] 回答",List.of())))).isFalse();
        failed(work);assertThat(store.task(work.task().id()).status()).isEqualTo("RUNNING");
    }
    private LumiChatDtos.Page chatPage(long user){return tx.execute(status->chatService.read(user,null));}
    private LumiChatDtos.Turn chatSend(long user,String question){var page=chatPage(user);return tx.execute(status->chatService.send(user,new LumiChatDtos.Send(page.sessionId(),UUID.randomUUID().toString(),question)));}
    private LumiChatService.Work chatClaim(){return tx.execute(status->chatService.claim());}
    private void chatComplete(LumiChatService.Work work){tx.executeWithoutResult(status->chatService.finish(work,new DeepSeekClient.Result("[E2E_TEST] 回答",null,null,100,20)));}
    private void chatAge(long user){jdbc.update("UPDATE lumi_chat_turns SET accepted_at=DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 6 SECOND) WHERE user_id=?",user);}
    @Test void chatRequestKeyAndSessionEnforceOwnershipWithoutDuplicateAdmission(){
        var page=chatPage(1);var request=new LumiChatDtos.Send(page.sessionId(),UUID.randomUUID().toString(),"[E2E_TEST] 问题");
        var first=tx.execute(status->chatService.send(1,request));
        assertThat(tx.execute(status->chatService.send(1,request)).id()).isEqualTo(first.id());
        assertThat(chatPage(2).turns()).isEmpty();
        assertThatThrownBy(()->tx.execute(status->chatService.send(2,request))).hasMessageContaining("暂不可用");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lumi_chat_turns",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lumi_attempts",Long.class)).isZero();
    }
    @Test void postAndChatShareActiveUserConcurrencyAndBudget(){
        chatSend(1,"[E2E_TEST] 聊天");assertThat(request(1,1,"@Lumi [E2E_TEST] 帖子").status()).isEqualTo("REJECTED");
        request(2,1,"@Lumi [E2E_TEST] 帖子");
        assertThatThrownBy(()->chatSend(2,"[E2E_TEST] 聊天")).hasMessageContaining("上一条");
        assertThat(claim()).isNotNull();assertThat(chatClaim()).isNotNull();
        chatSend(3,"[E2E_TEST] 等待");assertThat(chatClaim()).isNull();
        assertThat(jdbc.queryForObject("SELECT SUM(calls) FROM lumi_budget_days",Long.class)).isEqualTo(2);
        assertThat(store.allRunning()).isEqualTo(2);
    }
    @Test void chatDeleteErasesContentButRetainsQuotaAndCostAndDoesNotCreateNotifications(){
        var turn=chatSend(1,"[E2E_TEST] 私有问题");var session=chatPage(1).sessionId();
        assertThatThrownBy(()->tx.executeWithoutResult(status->chatService.delete(1,Long.parseLong(session)))).hasMessageContaining("停止");
        var work=chatClaim();assertThat(Boolean.TRUE.equals(tx.execute(status->chatService.sending(work)))).isTrue();chatComplete(work);
        chatComplete(work);
        assertThat(chats.turn(Long.parseLong(turn.id())).status()).isEqualTo("SUCCEEDED");
        long charged=jdbc.queryForObject("SELECT SUM(spent_micros) FROM lumi_budget_days",Long.class);
        tx.executeWithoutResult(status->chatService.delete(1,Long.parseLong(session)));tx.executeWithoutResult(status->chatService.delete(1,Long.parseLong(session)));
        var reset=chatPage(1);assertThat(reset.sessionId()).isNotEqualTo(session);assertThat(reset.turns()).isEmpty();
        assertThat(chats.turn(Long.parseLong(turn.id())).question()).isEmpty();assertThat(chats.turn(Long.parseLong(turn.id())).answer()).isNull();
        assertThat(chats.daily(1,store.day(store.now()))).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT SUM(spent_micros) FROM lumi_budget_days",Long.class)).isEqualTo(charged);
        verifyNoInteractions(events);
    }
    @Test void chatFailureKeepsCooldownAndAttemptsButReleasesSuccessfulAllowance(){
        var turn=chatSend(1,"[E2E_TEST] 失败");var work=chatClaim();
        tx.executeWithoutResult(status->chatService.sending(work));tx.executeWithoutResult(status->chatService.fail(work,"[E2E_TEST] 失败"));
        assertThat(chats.daily(1,store.day(store.now()))).isZero();
        assertThatThrownBy(()->tx.execute(status->chatService.retry(Long.parseLong(turn.id()),1))).hasMessageContaining("发送有点快");
        chatAge(1);tx.executeWithoutResult(status->chatService.retry(Long.parseLong(turn.id()),1));var second=chatClaim();
        tx.executeWithoutResult(status->chatService.fail(second,"[E2E_TEST] 失败"));chatAge(1);
        assertThatThrownBy(()->tx.execute(status->chatService.retry(Long.parseLong(turn.id()),1))).hasMessageContaining("最后一条");
        assertThat(chats.turn(Long.parseLong(turn.id())).attempts()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT SUM(spent_micros) FROM lumi_budget_days",Long.class)).isEqualTo(work.attempt().reserved());
    }
    @Test void chatHistoryIsPagedAndContextBelongsOnlyToCurrentUserAndSession(){
        chatProperties.setUserDaily(100);
        var session=Long.parseLong(chatPage(1).sessionId());var now=store.now();
        for(long id=1000;id<1023;id++){
            chats.insert(id,1,session,"E2E_TEST_"+id,"[E2E_TEST] 问题"+id,now);chats.finish(id,"SUCCEEDED","[E2E_TEST]","[E2E_TEST] 回答"+id,now);
        }
        var latest=chatPage(1);assertThat(latest.turns()).hasSize(20);assertThat(latest.hasMore()).isTrue();assertThat(latest.turns().getFirst().id()).isEqualTo("1003");
        var older=tx.execute(status->chatService.read(1,1003L));assertThat(older.turns()).hasSize(3);assertThat(older.hasMore()).isFalse();
        assertThat(chatPage(2).turns()).isEmpty();
        assertThat(chats.history(chats.turn(1022),6)).extracting(LumiChatRepository.Turn::id).containsExactly(1016L,1017L,1018L,1019L,1020L,1021L);
        var reset=tx.execute(status->chatService.reset(1,""+session));chatAge(1);var next=chatSend(1,"[E2E_TEST] 新对话");
        assertThat(chats.history(chats.turn(Long.parseLong(next.id())),6)).isEmpty();assertThat(reset.sessionId()).isNotEqualTo(""+session);
        assertThat(Instant.parse(latest.turns().getFirst().createdAt())).isNotNull();
    }
    @Test void chatDailyQuotaSurvivesResetAndWebRequestsDoNotCreateTasks(){
        chatProperties.setUserDaily(1);var page=chatPage(1);
        assertThatThrownBy(()->chatSend(1,"[E2E_TEST] 联网查一下")).hasMessageContaining("暂不支持联网");
        assertThat(chats.page(1,Long.parseLong(page.sessionId()),null,20)).isEmpty();
        chatSend(1,"[E2E_TEST] 问题");chatComplete(chatClaim());chatAge(1);
        tx.executeWithoutResult(status->chatService.reset(1,page.sessionId()));
        assertThatThrownBy(()->chatSend(1,"[E2E_TEST] 又问")).hasMessageContaining("次数已用完");
    }
    @Test void chatPreflightAndPublishRejectBannedUsersAndStaleLeases(){
        chatSend(1,"[E2E_TEST] 问题");var work=chatClaim();jdbc.update("UPDATE users SET is_banned=TRUE WHERE id=1");
        assertThat(Boolean.TRUE.equals(tx.execute(status->chatService.sending(work)))).isFalse();
        tx.executeWithoutResult(status->chatService.finish(work,new DeepSeekClient.Result("[E2E_TEST] 回答",null,null,100,20)));
        assertThat(chats.turn(work.turn().id()).answer()).isNull();
        assertThatThrownBy(()->chatPage(1)).hasMessageContaining("暂不可用");
        chatSend(2,"[E2E_TEST] 第二位");var second=chatClaim();jdbc.update("UPDATE lumi_chat_turns SET lease_token='[E2E_TEST] newer' WHERE id=?",second.turn().id());
        tx.executeWithoutResult(status->chatService.fail(second,"[E2E_TEST] 旧执行者"));assertThat(chats.turn(second.turn().id()).status()).isEqualTo("RUNNING");
    }
    @Test void chatBudgetRecoveryAndSharedQueueCapacityAreBounded(){
        p.setQueueCapacity(1);chatSend(1,"[E2E_TEST] 排队");
        assertThat(request(2,1,"@Lumi [E2E_TEST] 解释").message()).contains("有点忙");
        var work=chatClaim();tx.executeWithoutResult(status->chatService.sending(work));
        jdbc.update("UPDATE lumi_chat_turns SET lease_until=DATE_SUB(UTC_TIMESTAMP(),INTERVAL 1 SECOND) WHERE id=?",work.turn().id());
        assertThat(chatClaim()).isNull();assertThat(chats.turn(work.turn().id()).status()).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT SUM(spent_micros) FROM lumi_budget_days",Long.class)).isEqualTo(work.attempt().reserved());
        p.setDailyBudgetMicros(1);chatSend(2,"[E2E_TEST] 预算");assertThat(chatClaim()).isNull();
        assertThat(chatPage(2).turns().getFirst().message()).contains("服务额度");
    }
    @Test void chatAnswersCanExceedCommentLengthButRemainBounded(){
        chatSend(1,"[E2E_TEST] 详细解释");var work=chatClaim();
        String answer="[E2E_TEST] "+"说明".repeat(250);
        tx.executeWithoutResult(status->chatService.finish(work,new DeepSeekClient.Result(answer,null,null,100,200)));
        assertThat(chatPage(1).turns().getFirst().answer()).hasSize(answer.length()).isEqualTo(answer);
    }
    @Test void stoppingKeepsSlotAndPartialTextAndSettlesUnknownCostOnlyOnce(){
        chatProperties.setUserDaily(1);var sent=chatSend(1,"[E2E_TEST] 流式问题");var work=chatClaim();
        assertThat(Boolean.TRUE.equals(tx.execute(status->chatService.sending(work)))).isTrue();
        tx.executeWithoutResult(status->chatService.progress(work,"[E2E_TEST] 第一段"));
        long revision=chats.turn(work.turn().id()).revision();
        assertThat(tx.execute(status->chatService.stop(1,work.turn().id())).status()).isEqualTo("STOPPING");
        assertThat(store.activeForUser(1)).isEqualTo(1);assertThat(store.allRunning()).isEqualTo(1);
        assertThatThrownBy(()->tx.execute(status->chatService.stop(2,work.turn().id()))).hasMessageContaining("暂不可用");
        assertThatThrownBy(()->tx.executeWithoutResult(status->chatService.delete(1,Long.parseLong(chatPage(1).sessionId())))).hasMessageContaining("停止");
        tx.executeWithoutResult(status->chatService.checkpoint(work,"[E2E_TEST] 最后一小段"));
        tx.executeWithoutResult(status->chatService.fail(work,"[E2E_TEST] 取消"));
        tx.executeWithoutResult(status->chatService.fail(work,"[E2E_TEST] 重放"));
        var stopped=chatService.snapshot(1,Long.parseLong(sent.id()));assertThat(stopped.status()).isEqualTo("STOPPED");assertThat(stopped.answer()).contains("最后一小段");
        assertThat(Long.parseLong(stopped.revision())).isGreaterThan(revision);assertThat(store.activeForUser(1)).isZero();
        assertThat(chats.daily(1,store.day(store.now()))).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT SUM(spent_micros) FROM lumi_budget_days",Long.class)).isEqualTo(work.attempt().reserved());
        assertThat(jdbc.queryForObject("SELECT SUM(reserved_micros) FROM lumi_budget_days",Long.class)).isZero();
        tx.executeWithoutResult(status->chatService.finish(work,new DeepSeekClient.Result("[E2E_TEST] 迟到回答",null,null,100,20)));
        assertThat(chatService.snapshot(1,work.turn().id()).answer()).doesNotContain("迟到回答");
        chatAge(1);assertThatThrownBy(()->chatSend(1,"[E2E_TEST] 绕过额度")).hasMessageContaining("次数已用完");
    }
    @Test void stoppingQueuedTaskNeverReservesBudgetAndIsIdempotent(){
        var sent=chatSend(1,"[E2E_TEST] 取消排队");long id=Long.parseLong(sent.id());
        assertThat(tx.execute(status->chatService.stop(1,id)).status()).isEqualTo("STOPPED");
        assertThat(tx.execute(status->chatService.stop(1,id)).status()).isEqualTo("STOPPED");
        assertThat(chatClaim()).isNull();assertThat(chats.daily(1,store.day(store.now()))).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lumi_attempts",Long.class)).isZero();
    }
    @Test void partialFailureRetriesSameTurnAndExcludesPartialFromContext(){
        var sent=chatSend(1,"[E2E_TEST] 中断");var work=chatClaim();
        tx.executeWithoutResult(status->chatService.progress(work,"[E2E_TEST] 不完整"));tx.executeWithoutResult(status->chatService.fail(work,"[E2E_TEST] 中断"));
        assertThat(chatService.snapshot(1,work.turn().id()).answer()).contains("不完整");
        chatAge(1);assertThat(tx.execute(status->chatService.retry(Long.parseLong(sent.id()),1)).answer()).isNull();
        var retry=chatClaim();assertThat(retry.turn().id()).isEqualTo(work.turn().id());assertThat(retry.turn().revision()).isGreaterThan(work.turn().revision());
        tx.executeWithoutResult(status->chatService.progress(work,"[E2E_TEST] 旧工作器"));assertThat(chats.turn(work.turn().id()).answer()).isNull();
        chatComplete(retry);chatAge(1);chatSend(1,"[E2E_TEST] 下一问");var next=chatClaim();
        assertThat(chats.history(next.turn(),6)).hasSize(1).allSatisfy(t->assertThat(t.answer()).doesNotContain("不完整","旧工作器"));
    }
    @Test void abandonedStoppingLeaseRecoversAndPreservesPartialWithoutReplayingProvider(){
        chatSend(1,"[E2E_TEST] 停止后重启");var work=chatClaim();tx.executeWithoutResult(status->chatService.sending(work));
        tx.executeWithoutResult(status->chatService.progress(work,"[E2E_TEST] 已保存"));tx.executeWithoutResult(status->chatService.stop(1,work.turn().id()));
        jdbc.update("UPDATE lumi_chat_turns SET lease_until=DATE_SUB(UTC_TIMESTAMP(),INTERVAL 1 SECOND) WHERE id=?",work.turn().id());
        assertThat(chatClaim()).isNull();assertThat(chats.turn(work.turn().id()).status()).isEqualTo("STOPPED");assertThat(chats.turn(work.turn().id()).answer()).contains("已保存");
        assertThat(store.allRunning()).isZero();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lumi_attempts",Long.class)).isEqualTo(1);
    }
    private LumiChatDtos.Page newConversation(long user){return tx.execute(status->chatService.create(user,UUID.randomUUID().toString()));}
    @Test void newConversationKeepsOldMessagesAndReplayDoesNotCreateAnother(){
        var a=chatPage(1);var sent=chatSend(1,"[E2E_TEST] 原会话");chatComplete(chatClaim());String key=UUID.randomUUID().toString();
        var b=tx.execute(status->chatService.create(1,key));assertThat(b.sessionId()).isNotEqualTo(a.sessionId());assertThat(b.turns()).isEmpty();
        assertThat(tx.execute(status->chatService.create(1,key)).sessionId()).isEqualTo(b.sessionId());
        assertThat(chatService.read(1,Long.parseLong(a.sessionId()),null).turns()).extracting(LumiChatDtos.Turn::id).containsExactly(sent.id());
        assertThat(chatService.list(1,null).items()).hasSize(2);assertThat(chats.turn(Long.parseLong(sent.id())).answer()).isNotBlank();
        assertThat(tx.execute(status->chatService.reset(1,a.sessionId())).sessionId()).isEqualTo(tx.execute(status->chatService.reset(1,a.sessionId())).sessionId());
        assertThat(chats.turn(Long.parseLong(sent.id())).question()).contains("原会话");
    }
    @Test void backgroundGenerationSurvivesSelectingAnotherConversationAndCannotMixAnswers(){
        var a=chatPage(1);chatSend(1,"[E2E_TEST] A正在生成");var work=chatClaim();
        var b=newConversation(1);assertThat(b.busy()).isTrue();assertThat(b.activeSessionId()).isEqualTo(a.sessionId());assertThat(b.turns()).isEmpty();
        assertThat(Boolean.TRUE.equals(tx.execute(status->chatService.sending(work)))).isTrue();
        assertThatThrownBy(()->tx.execute(status->chatService.send(1,new LumiChatDtos.Send(b.sessionId(),UUID.randomUUID().toString(),"[E2E_TEST] B不能绕过并发")))).hasMessageContaining("上一条");
        tx.executeWithoutResult(status->chatService.progress(work,"[E2E_TEST] A部分"));assertThat(chatService.snapshot(1,work.turn().id()).sessionId()).isEqualTo(a.sessionId());chatComplete(work);
        assertThat(chatService.read(1,Long.parseLong(b.sessionId()),null).turns()).isEmpty();assertThat(chatService.read(1,Long.parseLong(a.sessionId()),null).turns().getFirst().answer()).contains("回答");
        assertThat(chatService.read(1,Long.parseLong(b.sessionId()),null).busy()).isFalse();
    }
    @Test void conversationOwnershipAppliesToReadRenameDeleteSendAndCreationKeys(){
        var a=chatPage(1);String key=UUID.randomUUID().toString();var b=tx.execute(status->chatService.create(1,key));var other=tx.execute(status->chatService.create(2,key));assertThat(other.sessionId()).isNotEqualTo(b.sessionId());
        long id=Long.parseLong(a.sessionId());
        assertThatThrownBy(()->chatService.read(2,id,null)).hasMessageContaining("暂不可用");
        assertThatThrownBy(()->tx.executeWithoutResult(status->chatService.rename(2,id,"[E2E_TEST] 越权"))).hasMessageContaining("暂不可用");
        assertThatThrownBy(()->tx.executeWithoutResult(status->chatService.delete(2,id))).hasMessageContaining("暂不可用");
        String operation=UUID.randomUUID().toString();var request=new LumiChatDtos.Send(a.sessionId(),operation,"[E2E_TEST] 同一操作");tx.executeWithoutResult(status->chatService.send(1,request));
        assertThatThrownBy(()->tx.execute(status->chatService.send(1,new LumiChatDtos.Send(b.sessionId(),operation,request.content())))).hasMessageContaining("操作标识");
        assertThat(chatService.list(2,null).items()).extracting(LumiChatDtos.Conversation::id).doesNotContain(a.sessionId(),b.sessionId());
    }
    @Test void customTitleSurvivesFirstMessageAndListUsesStableActivityCursor(){
        var first=chatPage(1);long id=Long.parseLong(first.sessionId());tx.executeWithoutResult(status->chatService.rename(1,id,"[E2E_TEST] 自定名称"));chatSend(1,"[E2E_TEST] 不覆盖名称");
        assertThat(chatService.read(1,id,null).title()).isEqualTo("[E2E_TEST] 自定名称");
        for(int i=0;i<23;i++)newConversation(1);
        var head=chatService.list(1,null);assertThat(head.items()).hasSize(20);assertThat(head.hasMore()).isTrue();var tail=chatService.list(1,head.nextCursor());assertThat(tail.items()).hasSize(4);assertThat(tail.hasMore()).isFalse();
        assertThat(tail.items()).extracting(LumiChatDtos.Conversation::id).doesNotContainAnyElementsOf(head.items().stream().map(LumiChatDtos.Conversation::id).toList());
        assertThatThrownBy(()->chatService.list(1,"not-valid")).hasMessageContaining("游标无效");
    }
    @Test void deletingOneConversationKeepsOtherContentAndGlobalQuotaAndStopsStalePublication(){
        chatProperties.setUserDaily(1);var a=chatPage(1);var sent=chatSend(1,"[E2E_TEST] 要删除的正文");var work=chatClaim();chatComplete(work);var b=newConversation(1);
        tx.executeWithoutResult(status->chatService.delete(1,Long.parseLong(a.sessionId())));tx.executeWithoutResult(status->chatService.checkpoint(work,"[E2E_TEST] 迟到内容"));
        assertThat(chats.turn(Long.parseLong(sent.id())).question()).isEmpty();assertThat(chats.turn(Long.parseLong(sent.id())).answer()).isNull();assertThat(chatService.read(1,Long.parseLong(b.sessionId()),null).sessionId()).isEqualTo(b.sessionId());
        assertThatThrownBy(()->chatService.read(1,Long.parseLong(a.sessionId()),null)).hasMessageContaining("暂不可用");assertThatThrownBy(()->chatService.snapshot(1,work.turn().id())).hasMessageContaining("暂不可用");
        chatAge(1);assertThatThrownBy(()->chatSend(1,"[E2E_TEST] 删除不能退还额度")).hasMessageContaining("次数已用完");
    }
    @Test void concurrentConversationCreationHonorsCapWithoutModelCalls() throws Exception {
        chatPage(1);chatProperties.setMaxConversations(2);var executor=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        try{
            var results=new ArrayList<Future<Boolean>>();for(int i=0;i<2;i++)results.add(executor.submit(()->{gate.await();try{newConversation(1);return true;}catch(com.luminous.api.common.BusinessException ex){return false;}}));
            gate.countDown();int accepted=0;for(var result:results)if(result.get(10,TimeUnit.SECONDS))accepted++;assertThat(accepted).isEqualTo(1);
            assertThat(chatService.list(1,null).items()).hasSize(2);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lumi_attempts",Long.class)).isZero();
        }finally{executor.shutdownNow();}
    }
    @Test void migrationKeepsCurrentAndUnclearedHistoryButDoesNotRestoreErasedBodies() throws Exception {
        var now=store.now();chats.session(1,501,now);chats.insert(502,1,501,"E2E_TEST_migration_current","[E2E_TEST] 保留旧会话",now);chats.finish(502,"SUCCEEDED","[E2E_TEST]","[E2E_TEST] 旧回答",now);
        chats.insert(503,1,500,"E2E_TEST_migration_erased","",now);chats.insert(602,2,601,"E2E_TEST_migration_orphan","[E2E_TEST] 未擦除历史",now);
        jdbc.execute("DROP TABLE lumi_chat_conversations");try(var connection=Objects.requireNonNull(jdbc.getDataSource()).getConnection()){ScriptUtils.executeSqlScript(connection,new FileSystemResource("src/main/resources/db/migration/V31__lumi_chat_conversations.sql"));}
        assertThat(chatPage(1).sessionId()).isEqualTo("501");assertThat(chatPage(1).turns().getFirst().answer()).contains("旧回答");assertThat(chatService.list(1,null).items()).extracting(LumiChatDtos.Conversation::id).containsExactly("501");
        assertThat(chatPage(2).sessionId()).isEqualTo("601");assertThat(chatService.list(2,null).items()).hasSize(1);
    }
    @Test void stoppedPartialContextCanContinueOnlyInsideItsOwnConversation(){
        var a=chatPage(1);chatSend(1,"[E2E_TEST] 故事第一段");var work=chatClaim();tx.executeWithoutResult(status->chatService.progress(work,"[E2E_TEST] 月亮上有一只猫"));tx.executeWithoutResult(status->chatService.stop(1,work.turn().id()));tx.executeWithoutResult(status->chatService.fail(work,"[E2E_TEST] 停止"));
        var b=newConversation(1);chatAge(1);var next=tx.execute(status->chatService.send(1,new LumiChatDtos.Send(b.sessionId(),UUID.randomUUID().toString(),"[E2E_TEST] 其他话题")));var otherWork=chatClaim();assertThat(chats.history(otherWork.turn(),6)).isEmpty();chatComplete(otherWork);
        chatAge(1);var continued=tx.execute(status->chatService.send(1,new LumiChatDtos.Send(a.sessionId(),UUID.randomUUID().toString(),"[E2E_TEST] 继续")));assertThat(chats.history(chats.turn(Long.parseLong(continued.id())),6)).hasSize(1).allSatisfy(row->{assertThat(row.status()).isEqualTo("STOPPED");assertThat(row.answer()).contains("月亮");});
    }
}
