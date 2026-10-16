package com.luminous.api.notification;

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
import static org.assertj.core.api.Assertions.assertThat;

/** 固定独立开发端口，自建带测试标记的数据库，验证真实 MySQL 事务和版本水位。 */
@EnabledIfEnvironmentVariable(named="LUMINOUS_MYSQL_REVIEW_PASSWORD",matches=".+")
class NotificationMySqlInboxTest {
    private final String database="luminous_e2e_notify_"+UUID.randomUUID().toString().replace("-","");
    private JdbcTemplate admin,jdbc;
    private NotificationInboxRepository inbox;
    private TransactionTemplate tx;
    private boolean created;
    @BeforeEach void setup() throws Exception {
        String user=System.getenv().getOrDefault("LUMINOUS_MYSQL_REVIEW_USER","root"), password=System.getenv("LUMINOUS_MYSQL_REVIEW_PASSWORD");
        String base="jdbc:mysql://127.0.0.1:33067/",options="?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        admin=new JdbcTemplate(new DriverManagerDataSource(base+options,user,password));
        admin.execute("CREATE DATABASE `"+database+"`"); created=true;
        var source=new DriverManagerDataSource(base+database+options,user,password);
        jdbc=new JdbcTemplate(source); inbox=new NotificationInboxRepository(jdbc); tx=new TransactionTemplate(new DataSourceTransactionManager(source));
        try(var connection=source.getConnection()) {
            for(String migration : List.of("V8__create_notifications.sql","V19__notification_inbox.sql","V20__notification_category_order.sql"))
                ScriptUtils.executeSqlScript(connection,new FileSystemResource("src/main/resources/db/migration/"+migration));
        }
    }
    private NotificationEvent like(long actor) { return new NotificationEvent(actor,1L,"LIKE_POST","POST",9L,"[E2E_TEST] 聚合"); }
    private long id() { return jdbc.queryForObject("SELECT id FROM notifications WHERE deleted_at IS NULL",Long.class); }
    @Test void dedupReadVersionAndSoftDeletion() {
        tx.executeWithoutResult(s->inbox.insertBatch(List.of(like(2),like(3),like(2))));
        long id=id(); assertThat(jdbc.queryForObject("SELECT interaction_count FROM notifications WHERE id=?",Long.class,id)).isEqualTo(2);
        tx.executeWithoutResult(s->inbox.read(id,1,2L));
        assertThat(inbox.unread(1).get("ALL")).isZero();
        tx.executeWithoutResult(s->inbox.insertBatch(List.of(like(4))));
        tx.executeWithoutResult(s->inbox.read(id,1,2L));
        assertThat(inbox.unread(1).get("ALL")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT read_at IS NOT NULL FROM notifications WHERE id=?",Boolean.class,id)).isTrue();
        tx.executeWithoutResult(s->inbox.readAll(1));
        assertThat(inbox.unread(1).get("ALL")).isZero();
        tx.executeWithoutResult(s->inbox.delete(id,1));
        tx.executeWithoutResult(s->inbox.insertBatch(List.of(like(2))));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE deleted_at IS NULL",Long.class)).isZero();
        tx.executeWithoutResult(s->inbox.insertBatch(List.of(like(5))));
        assertThat(id()).isNotEqualTo(id);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications",Long.class)).isEqualTo(2);
    }
    @Test void concurrentActorsAreNotLost() throws Exception {
        var pool=Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> futures=new ArrayList<>();
            for(int i=2;i<18;i++) { long actor=i; futures.add(pool.submit(()->tx.executeWithoutResult(s->inbox.insertBatch(List.of(like(actor)))))); }
            for(var future:futures) future.get(20,TimeUnit.SECONDS);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications",Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT interaction_count FROM notifications",Long.class)).isEqualTo(16);
        } finally { pool.shutdownNow(); }
    }
    @Test void rollbackDoesNotConsumeActorAndRetentionPreservesSystem() {
        try { tx.executeWithoutResult(s->{inbox.insertBatch(List.of(like(2)));throw new IllegalStateException("[E2E_TEST] 回滚");}); }
        catch(IllegalStateException expected) { }
        tx.executeWithoutResult(s->inbox.insertBatch(List.of(like(2))));
        tx.executeWithoutResult(s->inbox.insertBatch(List.of(new NotificationEvent(2L,1L,"SYSTEM","POST",9L,"[E2E_TEST] 系统"))));
        jdbc.update("UPDATE notifications SET updated_at=CURRENT_TIMESTAMP-INTERVAL 181 DAY");
        assertThat(inbox.purge(180)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT type FROM notifications",String.class)).isEqualTo("SYSTEM");
    }
    @AfterEach void cleanup() { if(created) admin.execute("DROP DATABASE `"+database+"`"); }
}
