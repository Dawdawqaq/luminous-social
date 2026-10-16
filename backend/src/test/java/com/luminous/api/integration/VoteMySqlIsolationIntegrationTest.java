package com.luminous.api.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luminous.api.mq.ReliableEventRepository;
import com.luminous.api.vote.VoteCommandService;
import com.luminous.api.vote.VoteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** 仅连接固定开发端口，自建临时数据库验证 MySQL 旧快照下的投票接收，结束后删除本次数据库。 */
@EnabledIfEnvironmentVariable(named = "LUMINOUS_MYSQL_REVIEW_PASSWORD", matches = ".+")
class VoteMySqlIsolationIntegrationTest {
    private static final String SERVER_URL = "jdbc:mysql://127.0.0.1:33067/";
    private static final String OPTIONS = "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
    private final String database = "luminous_e2e_review_" + UUID.randomUUID().toString().replace("-", "");
    private JdbcTemplate administration;
    private JdbcTemplate jdbc;
    private TransactionTemplate transaction;
    private VoteCommandService commands;
    private VoteRepository votes;
    private boolean created;

    @BeforeEach
    void prepareDatabase() {
        String username = System.getenv().getOrDefault("LUMINOUS_MYSQL_REVIEW_USER", "root");
        String password = System.getenv("LUMINOUS_MYSQL_REVIEW_PASSWORD");
        administration = new JdbcTemplate(new DriverManagerDataSource(SERVER_URL + OPTIONS, username, password));
        administration.execute("CREATE DATABASE `" + database + "`");
        created = true;
        var source = new DriverManagerDataSource(SERVER_URL + database + OPTIONS, username, password);
        jdbc = new JdbcTemplate(source);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        transaction.setTimeout(15);
        jdbc.execute("CREATE TABLE posts(id BIGINT PRIMARY KEY,title VARCHAR(100),author_id BIGINT DEFAULT 1) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE post_votes(post_id BIGINT,user_id BIGINT,value INT) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE post_vote_clocks(post_id BIGINT PRIMARY KEY,accepted_version BIGINT,accepted_total INT,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE vote_states(post_id BIGINT,user_id BIGINT,desired_value INT,accepted_version BIGINT,applied_value INT,applied_version BIGINT,PRIMARY KEY(post_id,user_id)) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE reliable_events(event_id VARCHAR(96) PRIMARY KEY,event_type VARCHAR(24),post_id BIGINT,user_id BIGINT,business_version BIGINT,payload TEXT,status VARCHAR(16)) ENGINE=InnoDB");
        jdbc.update("INSERT INTO posts(id,title) VALUES(1,'[E2E_TEST] 旧快照投票回归')");
        jdbc.update("INSERT INTO post_vote_clocks(post_id,accepted_version,accepted_total) VALUES(1,0,0)");
        jdbc.update("INSERT INTO vote_states VALUES(1,1,0,0,0,0),(1,2,0,0,0,0)");
        votes = new VoteRepository(jdbc);
        commands = new VoteCommandService(votes, new ReliableEventRepository(jdbc), new ObjectMapper(), event -> {});
    }

    @Test
    void sameUserSwitchUsesLatestVersionAndPriorValueDespiteEarlierSnapshot() throws Exception {
        verifySnapshotRace(1, -1, -1, -2);
    }

    @Test
    void differentUsersPreserveBothAcceptedVotesDespiteEarlierSnapshot() throws Exception {
        verifySnapshotRace(2, 1, 2, 1);
    }

    private void verifySnapshotRace(long secondUser, int secondValue, int expectedTotal, int expectedDelta) throws Exception {
        CountDownLatch snapshotReady = new CountDownLatch(1);
        CountDownLatch firstCommitted = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var second = executor.submit(() -> transaction.execute(status -> {
                // 模拟 HTTP 幂等外层事务先读取帖子，再等待另一请求完成投票。
                assertThat(jdbc.queryForObject("SELECT title FROM posts WHERE id=1", String.class)).contains("[E2E_TEST]");
                assertThat(jdbc.queryForObject("SELECT accepted_version FROM post_vote_clocks WHERE post_id=1", Long.class)).isZero();
                snapshotReady.countDown();
                try {
                    if (!firstCommitted.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("[E2E_TEST] 等待首次提交超时");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt(); throw new IllegalStateException(ex);
                }
                // 普通查询仍是旧快照，业务仓库的锁定读取必须得到最新水位。
                assertThat(jdbc.queryForObject("SELECT accepted_version FROM post_vote_clocks WHERE post_id=1", Long.class)).isZero();
                return commands.accept(1, secondUser, secondValue);
            }));
            assertThat(snapshotReady.await(10, TimeUnit.SECONDS)).isTrue();
            var first = transaction.execute(status -> commands.accept(1, 1, 1));
            assertThat(first.message().version()).isEqualTo(1);
            firstCommitted.countDown();
            var accepted = second.get(15, TimeUnit.SECONDS);
            assertThat(accepted.message().version()).isEqualTo(2);
            assertThat(accepted.message().delta()).isEqualTo(expectedDelta);
            assertThat(accepted.total()).isEqualTo(expectedTotal);
            assertThat(votes.clock(1).acceptedTotal()).isEqualTo(expectedTotal);
            assertThat(votes.state(1, secondUser).desiredValue()).isEqualTo(secondValue);
            assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT business_version) FROM reliable_events", Integer.class)).isEqualTo(2);
        } finally {
            firstCommitted.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @AfterEach
    void removeOwnedDatabase() {
        if (created) {
            if (!database.matches("luminous_e2e_review_[a-f0-9]{32}")) throw new IllegalStateException("[E2E_TEST] 清理范围无效");
            administration.execute("DROP DATABASE `" + database + "`");
        }
    }
}
