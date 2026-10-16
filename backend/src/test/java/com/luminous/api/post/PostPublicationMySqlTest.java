package com.luminous.api.post;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/** 仅在独立开发端口的临时库检查真实行锁、回滚和额度，不使用生产库。 */
@EnabledIfEnvironmentVariable(named = "LUMINOUS_MYSQL_REVIEW_PASSWORD", matches = ".+")
class PostPublicationMySqlTest {
    private final String database = "luminous_e2e_posting_" + UUID.randomUUID().toString().replace("-", "");
    private JdbcTemplate admin, jdbc;
    private TransactionTemplate transactions;
    private PostPublicationPolicy policy;
    private boolean created;
    @BeforeEach void setup() throws Exception {
        String user = System.getenv().getOrDefault("LUMINOUS_MYSQL_REVIEW_USER", "root"), password = System.getenv("LUMINOUS_MYSQL_REVIEW_PASSWORD");
        String base = "jdbc:mysql://127.0.0.1:33067/", options = "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        admin = new JdbcTemplate(new DriverManagerDataSource(base + options, user, password));
        admin.execute("CREATE DATABASE `" + database + "`"); created = true;
        var source = new DriverManagerDataSource(base + database + options, user, password);
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY,username VARCHAR(100)) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE communities(id BIGINT PRIMARY KEY) ENGINE=InnoDB");
        try (var connection = source.getConnection()) { ScriptUtils.executeSqlScript(connection, new FileSystemResource("src/main/resources/db/migration/V26__community_browse_and_post_limits.sql")); }
        jdbc.update("INSERT INTO users VALUES(1,'[E2E_TEST] posting')");
        policy = new PostPublicationPolicy(new PostPublicationRepository(jdbc));
        transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
    }
    @Test void failureAfterRecordingRollsBackAndCanRetryImmediately() {
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> { policy.check(1); policy.record(1, 10); throw new IllegalStateException("[E2E_TEST] 回滚"); })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM post_publications", Integer.class)).isZero();
        transactions.executeWithoutResult(status -> { policy.check(1); policy.record(1, 11); });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM post_publications", Integer.class)).isEqualTo(1);
    }
    @Test void concurrentPublishersShareOneSuccessfulSlot() throws Exception {
        jdbc.update("INSERT INTO post_publish_gates(user_id) VALUES(1)");
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = new ArrayList<Future<String>>();
            for (long id : List.of(10L, 11L)) futures.add(executor.submit(() -> {
                start.await();
                try { transactions.executeWithoutResult(status -> { policy.check(1); policy.record(1, id); }); return "success"; }
                catch (PostRateLimitException ex) { return "limited"; }
            }));
            start.countDown();
            assertThat(List.of(futures.get(0).get(10, TimeUnit.SECONDS), futures.get(1).get(10, TimeUnit.SECONDS))).containsExactlyInAnyOrder("success", "limited");
        }
    }
    @Test void deletingContentDoesNotReturnTheQuota() {
        transactions.executeWithoutResult(status -> { policy.check(1); policy.record(1, 10); });
        // 不建立帖子外键，帖子不存在时成功发布记录仍保留。
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> policy.check(1))).isInstanceOf(PostRateLimitException.class);
    }
    @Test void rollingWindowAndExpiredEntriesAreIndependentOfMinimumInterval() {
        jdbc.update("INSERT INTO post_publish_gates VALUES(1,NOW(6)-INTERVAL 40 SECOND)");
        for (int i = 0; i < 10; i++) jdbc.update("INSERT INTO post_publications VALUES(?,1,DATE_SUB(NOW(6),INTERVAL ? SECOND))", 100 + i, 59 + i * 45);
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> policy.check(1))).isInstanceOf(PostRateLimitException.class);
        jdbc.update("UPDATE post_publications SET published_at=NOW(6)-INTERVAL 601 SECOND");
        transactions.executeWithoutResult(status -> { policy.check(1); policy.record(1, 500); });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM post_publications", Integer.class)).isEqualTo(1);
    }
    @AfterEach void cleanup() { if (created) admin.execute("DROP DATABASE `" + database + "`"); }
}
