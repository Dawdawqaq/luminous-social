package com.luminous.api.lumi;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/** 仅在独立开发 MySQL 的随机专库验证完整 Flyway 升级与非事务失败恢复。 */
@EnabledIfEnvironmentVariable(named="LUMINOUS_MYSQL_REVIEW_PASSWORD",matches=".+")
class LumiMigrationMySqlTest {
    private final String database="luminous_e2e_lumi_migration_"+UUID.randomUUID().toString().replace("-","");
    private JdbcTemplate admin,jdbc;
    private DriverManagerDataSource source;

    @BeforeEach void setup() {
        String user=System.getenv().getOrDefault("LUMINOUS_MYSQL_REVIEW_USER","root");
        String password=System.getenv("LUMINOUS_MYSQL_REVIEW_PASSWORD");
        String base="jdbc:mysql://127.0.0.1:33067/",options="?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        admin=new JdbcTemplate(new DriverManagerDataSource(base+options,user,password));
        admin.execute("CREATE DATABASE `"+database+"` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        source=new DriverManagerDataSource(base+database+options,user,password);jdbc=new JdbcTemplate(source);
    }

    private Flyway flyway(String target) {
        var config=Flyway.configure().dataSource(source).locations("filesystem:src/main/resources/db/migration");
        if(target!=null)config.target(target);
        return config.load();
    }

    private Map<String,Object> legacyUser(String username) {
        flyway("27").migrate();
        jdbc.update("INSERT INTO users(id,username,display_name,password_hash,join_date,bio) VALUES(101,?,'[E2E_TEST] 历史用户','[E2E_TEST] 密码保持','[E2E_TEST]','[E2E_TEST] 简介保持')",username);
        return jdbc.queryForMap("SELECT id,username,display_name,password_hash,bio,role,is_banned FROM users WHERE id=101");
    }

    private void assertAssistant() {
        assertThat(jdbc.queryForMap("SELECT username,display_name,account_type,password_hash FROM users WHERE id=?",LumiProperties.BOT_ID))
                .containsEntry("username","lumi.bot").containsEntry("display_name","Lumi")
                .containsEntry("account_type","BOT").containsEntry("password_hash","!INTERNAL_BOT_NO_LOGIN!");
        jdbc.update("UPDATE users SET display_name='[E2E_TEST] Lumi' WHERE id=?",LumiProperties.BOT_ID);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version='28' AND success=1",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=? AND table_name IN ('lumi_tasks','lumi_chat_turns','lumi_chat_conversations')",Long.class,database)).isEqualTo(3);
    }

    @Test void emptyDatabaseMigratesWithIndependentInternalBotIdentity() {
        flyway(null).migrate();assertAssistant();
    }

    @ParameterizedTest @ValueSource(strings={"Lumi","lumi"})
    void legacyHumanLumiMigratesWithoutChangingAccount(String username) {
        var before=legacyUser(username);
        flyway(null).migrate();
        assertThat(jdbc.queryForMap("SELECT id,username,display_name,password_hash,bio,role,is_banned FROM users WHERE id=101")).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT account_type FROM users WHERE id=101",String.class)).isEqualTo("HUMAN");
        assertAssistant();
    }

    @Test void failedLegacyV28CanBeRepairedAndResumedWithoutDroppingHumanAccount() {
        var before=legacyUser("lumi");
        var legacy=Flyway.configure().dataSource(source).locations("filesystem:src/test/resources/db/lumi-legacy")
                .validateOnMigrate(false).load();
        assertThatThrownBy(legacy::migrate).isInstanceOf(org.flywaydb.core.api.FlywayException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=? AND table_name='users' AND column_name='account_type'",Long.class,database)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version='28' AND success=0",Long.class)).isEqualTo(1);
        var corrected=flyway(null);
        assertThatThrownBy(corrected::migrate).isInstanceOf(org.flywaydb.core.api.FlywayException.class);
        // 仅修复本例专库的失败记录；实际部署需先备份并核查非事务残留。
        corrected.repair();corrected.migrate();
        assertThat(jdbc.queryForMap("SELECT id,username,display_name,password_hash,bio,role,is_banned FROM users WHERE id=101")).isEqualTo(before);
        assertAssistant();
    }

    @Test void successfullyAppliedDraftRequiresExplicitChecksumReviewBeforeIdentityUpgrade() {
        flyway("27").migrate();
        var legacy=Flyway.configure().dataSource(source).locations("filesystem:src/test/resources/db/lumi-legacy")
                .validateOnMigrate(false).load();
        legacy.migrate();
        assertThat(jdbc.queryForObject("SELECT username FROM users WHERE id=?",String.class,LumiProperties.BOT_ID)).isEqualTo("Lumi");
        jdbc.update("UPDATE users SET display_name='[E2E_TEST] Lumi' WHERE id=?",LumiProperties.BOT_ID);
        jdbc.update("INSERT INTO users(id,username,display_name,password_hash,join_date) VALUES(101,'E2E_TEST_legacy_owner','[E2E_TEST] 已升级普通账号','[E2E_TEST] 不改密码','[E2E_TEST]')");
        var human=jdbc.queryForMap("SELECT * FROM users WHERE id=101");
        var corrected=flyway(null);
        var validation=corrected.validateWithResult();
        assertThat(validation.validationSuccessful).isFalse();
        // 独立校验还会报告待升级版本；这里只核对已应用历史，迁移仍执行默认完整校验。
        var historyProblems=validation.invalidMigrations.stream().filter(migration->migration.version!=null
                &&org.flywaydb.core.api.MigrationVersion.fromVersion(migration.version)
                .compareTo(org.flywaydb.core.api.MigrationVersion.fromVersion("28"))<=0).toList();
        assertThat(historyProblems).hasSize(1);
        assertThat(historyProblems.getFirst().version).isEqualTo("28");
        assertThatThrownBy(corrected::migrate).isInstanceOf(org.flywaydb.core.api.FlywayException.class);
        // 原成功草案的调整必须显式核对，不能在应用启动时自动忽略或修复所有校验错误。
        corrected.repair();corrected.migrate();
        assertThat(jdbc.queryForObject("SELECT username FROM users WHERE id=?",String.class,LumiProperties.BOT_ID)).isEqualTo("lumi.bot");
        assertThat(jdbc.queryForObject("SELECT account_type FROM users WHERE id=?",String.class,LumiProperties.BOT_ID)).isEqualTo("BOT");
        assertThat(jdbc.queryForObject("SELECT display_name FROM users WHERE id=?",String.class,LumiProperties.BOT_ID)).isEqualTo("[E2E_TEST] Lumi");
        assertThat(jdbc.queryForMap("SELECT * FROM users WHERE id=101")).isEqualTo(human);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version='32' AND success=1",Long.class)).isEqualTo(1);
    }

    @AfterEach void cleanup() {if(admin!=null)admin.execute("DROP DATABASE IF EXISTS `"+database+"`");}
}
