package com.luminous.api.search;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** 开发端口临时库的真实全文索引验证，结束删除，不把 H2 当成 ngram 证据。 */
@EnabledIfEnvironmentVariable(named = "LUMINOUS_MYSQL_REVIEW_PASSWORD", matches = ".+")
class SearchMySqlTest {
    private final String database = "luminous_e2e_search_" + UUID.randomUUID().toString().replace("-", "");
    private JdbcTemplate admin, jdbc;
    private SearchRepository repository;
    private DriverManagerDataSource source;
    private boolean created;
    @BeforeEach void setup() throws Exception {
        String user = System.getenv().getOrDefault("LUMINOUS_MYSQL_REVIEW_USER", "root"), password = System.getenv("LUMINOUS_MYSQL_REVIEW_PASSWORD");
        String base = "jdbc:mysql://127.0.0.1:33067/", options = "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        admin = new JdbcTemplate(new DriverManagerDataSource(base + options, user, password));
        admin.execute("CREATE DATABASE `" + database + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci"); created = true;
        source = new DriverManagerDataSource(base + database + options, user, password);
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE posts(id BIGINT PRIMARY KEY,author_id BIGINT,title VARCHAR(180),content TEXT,image_url VARCHAR(512),is_public BOOLEAN,status VARCHAR(20),votes INT DEFAULT 0) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY,username VARCHAR(64),display_name VARCHAR(96),bio VARCHAR(200),avatar_url VARCHAR(512),is_banned BOOLEAN DEFAULT FALSE) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE communities(id BIGINT PRIMARY KEY,slug VARCHAR(64),name VARCHAR(96),description VARCHAR(1024),icon_url VARCHAR(512)) ENGINE=InnoDB");
        jdbc.execute("CREATE FULLTEXT INDEX ft_idx_posts_search ON posts(title,content) WITH PARSER ngram");
        jdbc.execute("CREATE FULLTEXT INDEX ft_idx_users_search ON users(username,display_name) WITH PARSER ngram");
        try (var connection = source.getConnection()) { ScriptUtils.executeSqlScript(connection, new FileSystemResource("src/main/resources/db/migration/V27__search_relevance_indexes.sql")); }
        jdbc.update("INSERT INTO users(id,username,display_name,bio) VALUES(1,'E2E_TEST_author','[E2E_TEST] 作者','[E2E_TEST]')");
        repository = new SearchRepository(source);
    }
    private void post(long id, String title, String content) {
        jdbc.update("INSERT INTO posts(id,author_id,title,content,is_public,status) VALUES(?,1,?,?,TRUE,'NORMAL')", id, title, "[E2E_TEST] " + content);
    }
    @Test void exactPrefixAndTitleMatchesPrecedePopularBodyOnlyMatches() {
        post(10, "量子", "[E2E_TEST]"); post(11, "量子入门", "[E2E_TEST]"); post(12, "我的量子笔记", "[E2E_TEST]"); post(13, "[E2E_TEST] 热帖", "量子".repeat(20));
        jdbc.update("UPDATE posts SET votes=99999 WHERE id=13");
        assertThat(repository.candidates(SearchTerms.parse("量子"), "posts", 5)).containsExactly(10L, 11L, 12L, 13L);
    }
    @Test void chineseMultipleWordsUseAllTokensAndDoNotEnableBooleanOperators() {
        post(10, "量子学习", "[E2E_TEST]"); post(11, "量子笔记", "[E2E_TEST]"); post(12, "学习笔记", "[E2E_TEST]");
        assertThat(repository.candidates(SearchTerms.parse("+量子 -学习*"), "posts", 5)).containsExactly(10L);
    }
    @Test void singleCharacterAndSymbolQueriesUseBoundedPrefixAndExactMatching() {
        post(10, "猫猫学习", "[E2E_TEST]"); post(11, "[E2E_TEST] 学习", "猫猫"); post(12, "C++入门", "[E2E_TEST]");
        assertThat(repository.candidates(SearchTerms.parse("猫"), "posts", 5)).containsExactly(10L);
        assertThat(repository.candidates(SearchTerms.parse("C++"), "posts", 5)).containsExactly(12L);
    }
    @Test void repeatedNumericNgramsDoNotLoseEmbeddedTitleMatches() {
        for (String term : List.of("s000", "s111", "s121", "s123", "s999")) {
            jdbc.update("DELETE FROM posts");
            post(10, term + "[E2E_TEST]", term); post(11, "X" + term + "[E2E_TEST]", term);
            for (int i = 20; i < 60; i++) post(i, "[E2E_TEST]普通", term);
            assertThat(repository.candidates(SearchTerms.parse(term), "posts", 5)).as(term).startsWith(10L, 11L);
        }
    }
    @Test void numericUsernameIsNotReplacedWithUserIdAndExplicitIdIsSeparate() {
        jdbc.update("INSERT INTO users(id,username,display_name,bio) VALUES(20,'123','[E2E_TEST] 数字用户名','[E2E_TEST]'),(123,'other','[E2E_TEST] 编号','[E2E_TEST]')");
        assertThat(repository.candidates(SearchTerms.parse("@123"), "users", 5)).containsExactly(20L);
        assertThat(repository.candidates(SearchTerms.parse("id:123"), "users", 5)).containsExactly(123L);
    }
    @Test void currentVisibilityAndBanStateAreRecheckedWhenHydratingCachedIds() {
        post(10, "量子学习", "[E2E_TEST]"); post(11, "量子笔记", "[E2E_TEST]");
        var ids = repository.candidates(SearchTerms.parse("量子"), "posts", 5);
        jdbc.update("UPDATE posts SET is_public=FALSE WHERE id=10"); jdbc.update("UPDATE posts SET status='BANNED' WHERE id=11");
        assertThat(repository.hydrate("posts", ids)).isEmpty();
        jdbc.update("UPDATE users SET is_banned=TRUE WHERE id=1");
        assertThat(repository.hydrate("users", List.of(1L))).isEmpty();
    }
    @Test void communityExactNameOutranksDescriptionAndEscapedPrefixIsLiteral() {
        jdbc.update("INSERT INTO communities(id,slug,name,description) VALUES(10,'e2e_demo_','量子','[E2E_TEST]'),(11,'e2e_demox','[E2E_TEST]','量子量子量子')");
        assertThat(repository.candidates(SearchTerms.parse("量子"), "communities", 5)).startsWith(10L);
        var terms = SearchTerms.parse("c/e2e_demo_");
        assertThat(repository.candidates(terms, "communities", 5)).startsWith(10L);
        assertThat(repository.hydrate("communities", List.of(10L)).getFirst().url()).isEqualTo("/s/e2e_demo_");
    }
    @Test void realColdAndWarmSuggestionsHaveBoundedStatementBudgetAndNoCountOrProfileQueries() {
        post(10, "量子学习", "[E2E_TEST]");
        var statements = new ArrayList<String>();
        var observed = new org.springframework.jdbc.datasource.AbstractDataSource() {
            @Override public java.sql.Connection getConnection() throws java.sql.SQLException {
                var connection = source.getConnection();
                return (java.sql.Connection) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{java.sql.Connection.class}, (proxy, method, args) -> {
                    if ("prepareStatement".equals(method.getName())) statements.add((String) args[0]);
                    try { return method.invoke(connection, args); }
                    catch (java.lang.reflect.InvocationTargetException ex) { throw ex.getCause(); }
                });
            }
            @Override public java.sql.Connection getConnection(String user, String password) throws java.sql.SQLException { return getConnection(); }
        };
        var service = new SearchService(new SearchRepository(observed), org.mockito.Mockito.mock(org.springframework.data.redis.core.StringRedisTemplate.class), new com.fasterxml.jackson.databind.ObjectMapper(), java.time.Clock.systemUTC());
        var terms = SearchTerms.parse("量子");
        service.suggest(terms);
        // 三类候选各一次，只有实际召回帖子的分类读取一次摘要；空分类不读取资料。
        assertThat(statements).hasSize(4);
        service.suggest(terms);
        assertThat(statements).hasSize(5);
        assertThat(statements).noneMatch(sql -> sql.toLowerCase().contains("count("));
        assertThat(statements).noneMatch(sql -> sql.contains("growth_") || sql.contains("user_follows") || sql.contains("password_hash"));
    }
    @Test void explainActualPreparedCandidateQueryUsesFulltextAndPrefixIndexes() throws Exception {
        post(10, "量子学习", "[E2E_TEST]"); post(11, "[E2E_TEST]记录", "量子");
        var unrelated = new ArrayList<Object[]>();
        for (int i = 100; i < 1100; i++) unrelated.add(new Object[]{i, "[E2E_TEST]其他" + i, "[E2E_TEST]不匹配内容"});
        jdbc.batchUpdate("INSERT INTO posts(id,author_id,title,content,is_public,status) VALUES(?,1,?,?,TRUE,'NORMAL')", unrelated);
        var statementSql = new java.util.concurrent.atomic.AtomicReference<String>();
        Map<Integer, Object> bindings = new TreeMap<>();
        var observed = new org.springframework.jdbc.datasource.AbstractDataSource() {
            @Override public java.sql.Connection getConnection() throws java.sql.SQLException {
                var connection = source.getConnection();
                return (java.sql.Connection) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{java.sql.Connection.class}, (proxy, method, args) -> {
                    try {
                        Object result = method.invoke(connection, args);
                        if (!"prepareStatement".equals(method.getName())) return result;
                        statementSql.set((String) args[0]);
                        var statement = (java.sql.PreparedStatement) result;
                        return java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{java.sql.PreparedStatement.class}, (p, m, a) -> {
                            if (m.getName().startsWith("set") && a != null && a.length >= 2 && a[0] instanceof Integer index) bindings.put(index, a[1]);
                            try { return m.invoke(statement, a); }
                            catch (java.lang.reflect.InvocationTargetException ex) { throw ex.getCause(); }
                        });
                    } catch (java.lang.reflect.InvocationTargetException ex) { throw ex.getCause(); }
                });
            }
            @Override public java.sql.Connection getConnection(String user, String password) throws java.sql.SQLException { return getConnection(); }
        };
        assertThat(new SearchRepository(observed).candidates(SearchTerms.parse("量子"), "posts", 5)).isNotEmpty();
        var plan = jdbc.query(connection -> {
            var statement = connection.prepareStatement("EXPLAIN ANALYZE " + statementSql.get());
            for (var entry : bindings.entrySet()) statement.setObject(entry.getKey(), entry.getValue());
            return statement;
        }, (row, index) -> row.getString(1));
        String evidence = String.join("\n", plan);
        assertThat(evidence.toLowerCase()).contains("full-text");
        assertThat(evidence).doesNotContain("Table scan on posts");
        assertThat(evidence).contains("Index range scan on posts using idx_posts_search_title");
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/search-explain.txt"), evidence);
    }
    @AfterEach void cleanup() { if (created) admin.execute("DROP DATABASE `" + database + "`"); }
}
