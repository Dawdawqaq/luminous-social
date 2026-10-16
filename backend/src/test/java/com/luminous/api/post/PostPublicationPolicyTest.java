package com.luminous.api.post;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.*;

/** 使用独立内存数据库验证成功计数、事务回滚和删除后额度保留。 */
class PostPublicationPolicyTest {
    private JdbcTemplate jdbc;
    private TransactionTemplate transactions;
    private PostPublicationPolicy policy;

    @BeforeEach
    void setup() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:post-limit-" + java.util.UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE post_publish_gates(user_id BIGINT PRIMARY KEY,last_published_at DATETIME(6))");
        jdbc.execute("CREATE TABLE post_publications(post_id BIGINT PRIMARY KEY,user_id BIGINT,published_at DATETIME(6))");
        transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
        policy = new PostPublicationPolicy(new PostPublicationRepository(jdbc));
    }

    @Test
    void successfulPostBlocksTheSameUserAcrossRequests() {
        transactions.executeWithoutResult(status -> { policy.check(1); policy.record(1, 100); });
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> policy.check(1)))
                .isInstanceOfSatisfying(PostRateLimitException.class, error -> assertThat(error.getRetryAfterSeconds()).isBetween(1, 30));
        transactions.executeWithoutResult(status -> { policy.check(2); policy.record(2, 200); });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM post_publications", Integer.class)).isEqualTo(2);
    }

    @Test
    void rollbackDoesNotConsumeQuotaAndDeletionDoesNotRefundIt() {
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            policy.check(1); policy.record(1, 100); throw new IllegalStateException("[E2E_TEST] 写入失败");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM post_publications", Integer.class)).isZero();
        transactions.executeWithoutResult(status -> { policy.check(1); policy.record(1, 101); });
        // 成功记录不依赖帖子表，即使原帖子不再存在，冷却仍然生效。
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> policy.check(1))).isInstanceOf(PostRateLimitException.class);
    }

    @Test
    void rollingTenMinuteLimitRejectsTenPriorPublicationsAndExpiredEntriesStopCounting() {
        LocalDateTime now = new PostPublicationRepository(jdbc).now();
        jdbc.update("INSERT INTO post_publish_gates VALUES(1,?)", now.minusSeconds(40));
        for (int index = 0; index < 10; index++) jdbc.update("INSERT INTO post_publications VALUES(?,1,?)", 100 + index, now.minusSeconds(590 - index * 40));
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> policy.check(1))).isInstanceOf(PostRateLimitException.class);
        jdbc.update("UPDATE post_publications SET published_at=?", now.minusSeconds(601));
        transactions.executeWithoutResult(status -> { policy.check(1); policy.record(1, 500); });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM post_publications", Integer.class)).isEqualTo(1);
    }
}
