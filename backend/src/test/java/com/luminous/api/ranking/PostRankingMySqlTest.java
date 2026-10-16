package com.luminous.api.ranking;

import com.luminous.api.post.PostMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** 在独立开发端口的临时库验证真实 MySQL 聚合与候选召回，结束时删除临时库。 */
@EnabledIfEnvironmentVariable(named = "LUMINOUS_MYSQL_REVIEW_PASSWORD", matches = ".+")
class PostRankingMySqlTest {
    private final String database = "luminous_e2e_ranking_" + UUID.randomUUID().toString().replace("-", "");
    private final LocalDateTime now = LocalDateTime.of(2026, 10, 15, 12, 0);
    private JdbcTemplate admin, jdbc;
    private PostRankingRepository repository;
    private boolean created;

    @BeforeEach void setup() throws Exception {
        String user = System.getenv().getOrDefault("LUMINOUS_MYSQL_REVIEW_USER", "root");
        String password = System.getenv("LUMINOUS_MYSQL_REVIEW_PASSWORD");
        String base = "jdbc:mysql://127.0.0.1:33067/";
        String options = "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&rewriteBatchedStatements=true";
        admin = new JdbcTemplate(new DriverManagerDataSource(base + options, user, password));
        admin.execute("CREATE DATABASE `" + database + "`"); created = true;
        var source = new DriverManagerDataSource(base + database + options, user, password);
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE posts(id BIGINT PRIMARY KEY,author_id BIGINT,community_id BIGINT,title VARCHAR(100),is_public BOOLEAN,status VARCHAR(20),created_at TIMESTAMP,votes INT DEFAULT 0)");
        jdbc.execute("CREATE TABLE vote_states(post_id BIGINT,user_id BIGINT,desired_value INT,PRIMARY KEY(post_id,user_id))");
        jdbc.execute("CREATE TABLE comments(id BIGINT PRIMARY KEY,post_id BIGINT,author_id BIGINT,content VARCHAR(100))");
        jdbc.execute("CREATE TABLE community_members(user_id BIGINT,community_id BIGINT)");
        try (var connection = source.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource("src/main/resources/db/migration/V25__post_ranking_indexes.sql"));
        }
        repository = new PostRankingRepository(new NamedParameterJdbcTemplate(source), mock(PostMapper.class));
    }

    private void post(long id, long author, long community, LocalDateTime createdAt, boolean visible, String status) {
        jdbc.update("INSERT INTO posts(id,author_id,community_id,title,is_public,status,created_at) VALUES(?,?,?,?,?,?,?)",
                id, author, community, "[E2E_TEST] 排序 " + id, visible, status, createdAt);
    }

    @Test void acceptedVoteStatesOverrideLaggingProjectionAndAuthorsOwnInteractionsDoNotCount() {
        post(1, 1, 1, now.minusHours(1), true, "NORMAL");
        jdbc.update("UPDATE posts SET votes=999 WHERE id=1");
        jdbc.update("INSERT INTO vote_states VALUES(1,1,1),(1,2,1),(1,3,-1),(1,4,0)");
        jdbc.update("INSERT INTO comments VALUES(1,1,1,'[E2E_TEST] 自己'),(2,1,2,'[E2E_TEST] 一次'),(3,1,2,'[E2E_TEST] 多次')");
        jdbc.update("INSERT INTO comments VALUES(4,1,880000000000000001,'[E2E_TEST] Lumi 不参与热度')");
        var candidate = repository.candidates(now, null).getFirst();
        assertThat(candidate.netVotes()).isZero(); assertThat(candidate.participants()).isEqualTo(1);
        jdbc.update("UPDATE vote_states SET desired_value=1 WHERE post_id=1 AND user_id=3");
        assertThat(repository.candidates(now, null).getFirst().netVotes()).isEqualTo(2);
        jdbc.update("UPDATE vote_states SET desired_value=0 WHERE post_id=1 AND user_id=2");
        assertThat(repository.candidates(now, null).getFirst().netVotes()).isEqualTo(1);
    }

    @Test void participantsAreDistinctCappedAndRemovedOnlyWhenTheirLastCommentIsDeleted() {
        post(1, 1, 1, now.minusHours(1), true, "PUBLISHED");
        jdbc.update("INSERT INTO comments VALUES(1,1,2,'[E2E_TEST] 一'),(2,1,2,'[E2E_TEST] 二')");
        assertThat(repository.candidates(now, null).getFirst().participants()).isEqualTo(1);
        jdbc.update("DELETE FROM comments WHERE id=1");
        assertThat(repository.candidates(now, null).getFirst().participants()).isEqualTo(1);
        jdbc.update("DELETE FROM comments WHERE id=2");
        assertThat(repository.candidates(now, null).getFirst().participants()).isZero();
        for (int i = 2; i <= 15; i++) jdbc.update("INSERT INTO comments VALUES(?,1,?,'[E2E_TEST] 参与')", i, i);
        assertThat(repository.candidates(now, null).getFirst().participants()).isEqualTo(10);
    }

    @Test void publicationWindowCommunityAndVisibilityAreAppliedBeforeAggregation() {
        post(1, 1, 1, now.minusDays(7), true, "NORMAL");
        post(2, 2, 2, now, true, "PUBLISHED");
        post(3, 3, 1, now.minusDays(7).minusSeconds(1), true, "NORMAL");
        post(4, 4, 1, now.plusSeconds(1), true, "NORMAL");
        post(5, 5, 1, now, false, "NORMAL");
        post(6, 6, 1, now, true, "BANNED");
        assertThat(repository.candidates(now, null)).extracting(PostRankingPolicy.Candidate::id).containsExactlyInAnyOrder(1L, 2L);
        assertThat(repository.candidates(now, 1L)).extracting(PostRankingPolicy.Candidate::id).containsExactly(1L);
    }

    @Test void dayRecallDoesNotDisappearBehindMoreThan1500HighScoringOlderPosts() {
        var posts = new ArrayList<Object[]>();
        var states = new ArrayList<Object[]>();
        for (long id = 1; id <= 2200; id++) {
            boolean day = id > 1600;
            posts.add(new Object[]{id, id, 1L, "[E2E_TEST] 候选 " + id, true, "NORMAL", now.minusHours(day ? 23 : 25)});
            for (long actor = 1; actor <= (day ? 3 : 10); actor++) states.add(new Object[]{id, actor + 10000, 1});
        }
        jdbc.batchUpdate("INSERT INTO posts(id,author_id,community_id,title,is_public,status,created_at) VALUES(?,?,?,?,?,?,?)", posts);
        jdbc.batchUpdate("INSERT INTO vote_states VALUES(?,?,?)", states);
        var candidates = repository.candidates(now, null);
        var hot = PostRankingPolicy.hot(candidates, now, "day");
        assertThat(candidates.size()).isLessThanOrEqualTo(3000);
        assertThat(hot).hasSize(500);
        assertThat(hot.getFirst()).isEqualTo(2200L);
        assertThat(hot.getLast()).isEqualTo(1701L);
    }

    @AfterEach void cleanup() { if (created) admin.execute("DROP DATABASE `" + database + "`"); }
}
