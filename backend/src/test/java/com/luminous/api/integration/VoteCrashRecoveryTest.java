package com.luminous.api.integration;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;

/** 实际强制退出 JVM 后，通过独立文件数据库验证重启恢复，避免依赖内存对象模拟重启。 */
@EnabledIfEnvironmentVariable(named="VOTE_TEST_REDIS_PORT", matches="\\d+")
class VoteCrashRecoveryTest {
    @Test void restartAfterRedisUpdate_ReplaysCommittedIntentExactlyOnce() throws Exception {
        int port=Integer.parseInt(System.getenv("VOTE_TEST_REDIS_PORT"));
        assertThat(port).isNotEqualTo(6379).isNotEqualTo(63790).isNotEqualTo(63797);
        Path directory = Files.createTempDirectory(Path.of("target"), "vote-crash-").toAbsolutePath();
        String url = "jdbc:h2:file:" + directory.resolve("database").toString().replace('\\','/')
                + ";MODE=MySQL;NON_KEYWORDS=VALUE;DB_CLOSE_ON_EXIT=FALSE;WRITE_DELAY=0";
        JdbcDataSource dataSource = new JdbcDataSource(); dataSource.setURL(url); dataSource.setUser("sa"); dataSource.setPassword("");
        new ResourceDatabasePopulator(new ClassPathResource("vote-validation-schema.sql")).execute(dataSource);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO users(id,username,display_name,password_hash,join_date) VALUES(?, 'E2E_TEST_crash', '[E2E_TEST] 进程恢复', 'test', '2026')", VoteCrashProbe.USER_ID);
        jdbc.update("INSERT INTO communities(id,slug,name,description,rules_json,moderators_json) VALUES(900000003,'e2e-crash','[E2E_TEST] 崩溃验收','[E2E_TEST]','[]','[]')");
        jdbc.update("INSERT INTO posts(id,author_id,community_id,title) VALUES(?,?,900000003,'[E2E_TEST] 进程中断')", VoteCrashProbe.POST_ID,VoteCrashProbe.USER_ID);
        try {
            assertThat(runProcess(directory,"crash",url)).isEqualTo(23);
            assertThat(jdbc.queryForObject("SELECT votes FROM posts WHERE id=?",Integer.class,VoteCrashProbe.POST_ID)).isZero();
            assertThat(jdbc.queryForObject("SELECT status FROM reliable_events",String.class)).isEqualTo("PENDING");
            assertThat(runProcess(directory,"consume-crash",url)).isEqualTo(24);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM post_votes",Long.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM consumed_events",Long.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT status FROM reliable_events",String.class)).isEqualTo("PENDING");
            assertThat(runProcess(directory,"recover",url)).isZero();
            assertThat(jdbc.queryForObject("SELECT votes FROM posts WHERE id=?",Integer.class,VoteCrashProbe.POST_ID)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM post_votes",Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM consumed_events",Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT status FROM reliable_events",String.class)).isEqualTo("CONSUMED");
        } finally {
            LettuceConnectionFactory redis = new LettuceConnectionFactory("127.0.0.1",Integer.parseInt(System.getenv("VOTE_TEST_REDIS_PORT")));
            redis.afterPropertiesSet(); redis.start();
            try {
                StringRedisTemplate template = new StringRedisTemplate(redis);
                template.delete(List.of("luminous:stat:post:votes:"+VoteCrashProbe.POST_ID,
                        "luminous:vote:state:post:"+VoteCrashProbe.POST_ID,"luminous:vote:version:post:"+VoteCrashProbe.POST_ID,
                        "luminous:cache:post:detail:"+VoteCrashProbe.POST_ID,"luminous:stat:post:uv:"+VoteCrashProbe.POST_ID));
            } finally { redis.destroy(); }
            // 仅删除本次在 target 下创建的数据库文件，保留子进程日志供审查。
            Files.deleteIfExists(directory.resolve("database.mv.db"));
            Files.deleteIfExists(directory.resolve("database.trace.db"));
        }
    }

    private int runProcess(Path directory, String mode, String url) throws Exception {
        String classpath=System.getProperty("surefire.test.class.path");
        assertThat(classpath).isNotBlank();
        Path argumentFile=directory.resolve(mode+".args");
        // 参数文件规避 Windows 命令长度限制，路径作为独立参数传给 JVM。
        Files.writeString(argumentFile,"-Xmx256m\n-cp\n"+quote(classpath)+"\n"+VoteCrashProbe.class.getName()+"\n"+mode+"\n"+quote(url)+"\n");
        Path executable=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java");
        Process process=new ProcessBuilder(executable.toString(),"@"+argumentFile).redirectErrorStream(true)
                .redirectOutput(directory.resolve(mode+".log").toFile()).start();
        try {
            assertThat(process.waitFor(60,TimeUnit.SECONDS)).as("子进程日志：%s",directory.resolve(mode+".log")).isTrue();
            return process.exitValue();
        } finally { if(process.isAlive()){process.destroyForcibly().waitFor(5,TimeUnit.SECONDS);} }
    }

    private String quote(String value) { return "\""+value.replace("\\","\\\\").replace("\"","\\\"")+"\""; }
}
