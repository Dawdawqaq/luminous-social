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
class CommentPublicationPolicyTest {
    private JdbcTemplate jdbc;
    private TransactionTemplate transactions;
    private CommentPublicationPolicy policy;

    @BeforeEach
    void setup() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:comment-limit-" + java.util.UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE comment_publish_gates(user_id BIGINT PRIMARY KEY,last_published_at DATETIME(6))");
        jdbc.execute("CREATE TABLE comment_publications(comment_id BIGINT PRIMARY KEY,user_id BIGINT,published_at DATETIME(6))");
        transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
        policy = new CommentPublicationPolicy(new CommentPublicationRepository(jdbc));
    }

    @Test
    void successfulCommentBlocksTheSameUserAcrossRequests() {
        transactions.executeWithoutResult(status -> { policy.check(1); policy.record(1, 100); });
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> policy.check(1)))
                .isInstanceOfSatisfying(CommentRateLimitException.class, error -> assertThat(error.getRetryAfterSeconds()).isBetween(1, 5));
        transactions.executeWithoutResult(status -> { policy.check(2); policy.record(2, 200); });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comment_publications", Integer.class)).isEqualTo(2);
    }

    @Test
    void rollbackDoesNotConsumeQuotaAndDeletionDoesNotRefundIt() {
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            policy.check(1); policy.record(1, 100); throw new IllegalStateException("[E2E_TEST] 写入失败");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comment_publications", Integer.class)).isZero();
        transactions.executeWithoutResult(status -> { policy.check(1); policy.record(1, 101); });
        // 成功记录不依赖评论表，即使原评论不再存在，冷却仍然生效。
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> policy.check(1))).isInstanceOf(CommentRateLimitException.class);
    }

    @Test
    void rollingMinuteLimitRejectsTenPriorPublicationsAndExpiredEntriesStopCounting() {
        LocalDateTime now = new CommentPublicationRepository(jdbc).now();
        jdbc.update("INSERT INTO comment_publish_gates VALUES(1,?)", now.minusSeconds(10));
        for (int index = 0; index < 10; index++) jdbc.update("INSERT INTO comment_publications VALUES(?,1,?)", 100 + index, now.minusSeconds(55 - index * 5));
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> policy.check(1))).isInstanceOf(CommentRateLimitException.class);
        jdbc.update("UPDATE comment_publications SET published_at=?", now.minusSeconds(61));
        transactions.executeWithoutResult(status -> { policy.check(1); policy.record(1, 500); });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comment_publications", Integer.class)).isEqualTo(1);
    }
}
