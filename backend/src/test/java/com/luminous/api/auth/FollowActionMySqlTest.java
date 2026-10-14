package com.luminous.api.auth;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/** 固定开发端口，以临时数据库验证生产仓储的并发、旧快照、限频回滚和通知窗口。 */
@EnabledIfEnvironmentVariable(named="LUMINOUS_MYSQL_REVIEW_PASSWORD",matches=".+")
class FollowActionMySqlTest {
    private final String database="luminous_e2e_follow_"+UUID.randomUUID().toString().replace("-","");
    private JdbcTemplate admin,jdbc;
    private TransactionTemplate tx;
    private FollowActionPolicy policy;
    private boolean created;
    private record Result(boolean changed,boolean notification) {}

    @BeforeEach void setup() throws Exception {
        new com.luminous.api.common.SnowflakeIdGenerator(30,30);
        String user=System.getenv().getOrDefault("LUMINOUS_MYSQL_REVIEW_USER","root"),password=System.getenv("LUMINOUS_MYSQL_REVIEW_PASSWORD");
        String base="jdbc:mysql://127.0.0.1:33067/",options="?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        admin=new JdbcTemplate(new DriverManagerDataSource(base+options,user,password));
        admin.execute("CREATE DATABASE `"+database+"`");created=true;
        var source=new DriverManagerDataSource(base+database+options,user,password);
        jdbc=new JdbcTemplate(source);tx=new TransactionTemplate(new DataSourceTransactionManager(source));
        tx.setTimeout(20);
        jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY,username VARCHAR(96)) ENGINE=InnoDB");
        for(int i=1;i<=18;i++) jdbc.update("INSERT INTO users VALUES(?,?)",i,"[E2E_TEST] actor "+i);
        try(var connection=source.getConnection()) {
            for(String migration:List.of("V4__add_user_follows.sql","V21__follow_action_policy.sql","V22__follow_actor_window.sql"))
                ScriptUtils.executeSqlScript(connection,new FileSystemResource("src/main/resources/db/migration/"+migration));
        }
        policy=new FollowActionPolicy(new FollowActionRepository(jdbc));
    }
    private Result change(long actor,long target,boolean following) {
        return tx.execute(status->{boolean changed=policy.change(actor,target,following);
            return new Result(changed,changed && following && policy.shouldNotify(actor,target));});
    }
    private void releaseInterval(long actor) {
        jdbc.update("UPDATE follow_action_gates SET last_changed_at=CURRENT_TIMESTAMP(6)-INTERVAL 2 SECOND WHERE user_id=?",actor);
    }
    @Test void repeatsAreSuccessfulAndChangedStateIsThrottledWithoutLosingRelation() {
        assertThat(change(1,2,true)).isEqualTo(new Result(true,true));
        assertThat(change(1,2,true)).isEqualTo(new Result(false,false));
        assertThatThrownBy(()->change(1,2,false)).isInstanceOf(FollowRateLimitException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_follows",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM follow_actions",Long.class)).isEqualTo(1);
        releaseInterval(1);assertThat(change(1,2,false).changed()).isTrue();
        assertThat(change(1,2,false).changed()).isFalse();
        releaseInterval(1);assertThat(change(1,2,true)).isEqualTo(new Result(true,false));
        releaseInterval(1);change(1,2,false);
        jdbc.update("UPDATE follow_notification_windows SET last_event_at=CURRENT_TIMESTAMP(6)-INTERVAL 11 MINUTE");
        releaseInterval(1);assertThat(change(1,2,true).notification()).isTrue();
    }
    @Test void concurrentSamePairCreatesExactlyOneChangeAndOneNotification() throws Exception {
        var pool=Executors.newFixedThreadPool(8);
        try {
            List<Future<Result>> futures=new ArrayList<>();
            for(int i=0;i<16;i++) futures.add(pool.submit(()->change(1,2,true)));
            List<Result> results=new ArrayList<>();for(var future:futures)results.add(future.get(20,TimeUnit.SECONDS));
            assertThat(results.stream().filter(Result::changed).count()).isEqualTo(1);
            assertThat(results.stream().filter(Result::notification).count()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_follows",Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM follow_actions",Long.class)).isEqualTo(1);
        } finally {pool.shutdownNow();}
    }
    @Test void differentActorsDoNotShareReceiverLockOrMissingRowGapLocks() throws Exception {
        var pool=Executors.newFixedThreadPool(8);
        try {
            List<Future<Result>> futures=new ArrayList<>();
            for(int i=2;i<=17;i++){long actor=i;futures.add(pool.submit(()->change(actor,1,true)));}
            for(var future:futures) assertThat(future.get(20,TimeUnit.SECONDS)).isEqualTo(new Result(true,true));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_follows WHERE following_id=1",Long.class)).isEqualTo(16);
        } finally {pool.shutdownNow();}
    }
    @Test void oldRepeatableReadSnapshotDoesNotReinsertExistingRelation() throws Exception {
        CountDownLatch snapshot=new CountDownLatch(1),committed=new CountDownLatch(1);
        var pool=Executors.newSingleThreadExecutor();
        try {
            var repeated=pool.submit(()->tx.execute(status->{
                jdbc.queryForObject("SELECT COUNT(*) FROM user_follows",Long.class);snapshot.countDown();
                try {if(!committed.await(10,TimeUnit.SECONDS))throw new IllegalStateException("[E2E_TEST] 等待超时");}
                catch(InterruptedException ex){Thread.currentThread().interrupt();throw new IllegalStateException(ex);}
                return policy.change(1,2,true);
            }));
            assertThat(snapshot.await(10,TimeUnit.SECONDS)).isTrue();change(1,2,true);committed.countDown();
            assertThat(repeated.get(15,TimeUnit.SECONDS)).isFalse();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM follow_actions",Long.class)).isEqualTo(1);
        } finally {pool.shutdownNow();}
    }
    @Test void failureRollsBackQuotaAndNotificationSlot() {
        assertThatThrownBy(()->tx.executeWithoutResult(status->{policy.change(1,2,true);policy.shouldNotify(1,2);throw new IllegalStateException("[E2E_TEST] 通知意图保存失败");})).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM follow_actions",Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM follow_notification_windows",Long.class)).isZero();
        assertThat(change(1,2,true)).isEqualTo(new Result(true,true));
    }
    @Test void rollingMinuteLimitRollsBackAttemptedRelationshipChange() {
        jdbc.update("INSERT INTO follow_action_gates(user_id,last_changed_at) VALUES(1,CURRENT_TIMESTAMP(6)-INTERVAL 2 SECOND)");
        for(int i=1;i<=30;i++)jdbc.update("INSERT INTO follow_actions(id,user_id,target_id,is_following,changed_at) VALUES(?,1,2,true,CURRENT_TIMESTAMP(6)-INTERVAL ? SECOND)",i,i);
        String times=jdbc.query("SELECT changed_at FROM follow_actions ORDER BY changed_at",
                (rs,row)->rs.getObject(1,java.time.LocalDateTime.class).toString()).stream().collect(java.util.stream.Collectors.joining(","));
        jdbc.update("UPDATE follow_action_gates SET recent_change_times=? WHERE user_id=1",times);
        assertThatThrownBy(()->change(1,2,true)).isInstanceOfSatisfying(FollowRateLimitException.class,ex->assertThat(ex.getRetryAfterSeconds()).isBetween(29,30));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_follows",Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM follow_actions",Long.class)).isEqualTo(30);
    }
    @AfterEach void cleanup(){if(created)admin.execute("DROP DATABASE `"+database+"`");}
}
