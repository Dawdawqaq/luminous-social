package com.luminous.api.auth;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.*;

/** 使用独立开发端口的临时 MySQL 库验证缺失图片回填，不修改真实用户资料。 */
@EnabledIfEnvironmentVariable(named="LUMINOUS_MYSQL_REVIEW_PASSWORD",matches=".+")
class ProfilePresetMigrationMySqlTest {
    private final String database="luminous_e2e_profile_"+UUID.randomUUID().toString().replace("-","");
    private JdbcTemplate admin,jdbc;
    private DriverManagerDataSource source;
    private boolean created;

    @BeforeEach void setup() {
        String user=System.getenv().getOrDefault("LUMINOUS_MYSQL_REVIEW_USER","root"),password=System.getenv("LUMINOUS_MYSQL_REVIEW_PASSWORD");
        String base="jdbc:mysql://127.0.0.1:33067/",options="?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        admin=new JdbcTemplate(new DriverManagerDataSource(base+options,user,password));
        admin.execute("CREATE DATABASE `"+database+"`");created=true;
        source=new DriverManagerDataSource(base+database+options,user,password);jdbc=new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY,display_name VARCHAR(96),avatar_url VARCHAR(512),banner_url VARCHAR(512))");
    }
    private void migrate() throws Exception {
        try(var connection=source.getConnection()) {
            ScriptUtils.executeSqlScript(connection,new FileSystemResource("src/main/resources/db/migration/V24__profile_image_presets.sql"));
        }
    }
    @Test void missingFieldsAreFilledIndependentlyAndCustomImagesArePreserved() throws Exception {
        jdbc.update("INSERT INTO users VALUES(1,'[E2E_TEST] 自定义','/uploads/avatar.png','/uploads/banner.png')");
        jdbc.update("INSERT INTO users VALUES(2,'[E2E_TEST] 缺背景','/uploads/avatar.png','  ')");
        jdbc.update("INSERT INTO users VALUES(3,'[E2E_TEST] 缺头像',NULL,'https://example.com/banner.png')");
        migrate();
        assertThat(jdbc.queryForObject("SELECT avatar_url FROM users WHERE id=1",String.class)).isEqualTo("/uploads/avatar.png");
        assertThat(jdbc.queryForObject("SELECT banner_url FROM users WHERE id=1",String.class)).isEqualTo("/uploads/banner.png");
        assertThat(jdbc.queryForObject("SELECT avatar_url FROM users WHERE id=2",String.class)).isEqualTo("/uploads/avatar.png");
        assertThat(jdbc.queryForObject("SELECT banner_url FROM users WHERE id=3",String.class)).isEqualTo("https://example.com/banner.png");
        assertThat(ProfilePresetCatalog.catalog().banners()).extracting(ProfilePresetCatalog.Preset::url)
                .contains(jdbc.queryForObject("SELECT banner_url FROM users WHERE id=2",String.class));
        assertThat(ProfilePresetCatalog.catalog().avatars()).extracting(ProfilePresetCatalog.Preset::url)
                .contains(jdbc.queryForObject("SELECT avatar_url FROM users WHERE id=3",String.class));
        var first=jdbc.queryForList("SELECT * FROM users ORDER BY id");migrate();
        assertThat(jdbc.queryForList("SELECT * FROM users ORDER BY id")).isEqualTo(first);
    }
    @Test void backfillCombinationsMatchTheCatalogAndEveryAssetExists() throws Exception {
        int combinations = ProfilePresetCatalog.catalog().avatars().size() * ProfilePresetCatalog.catalog().banners().size();
        for(int i=0;i<combinations;i++)jdbc.update("INSERT INTO users(id,display_name) VALUES(?,?)",i,"[E2E_TEST] 预设 "+i);
        migrate();
        assertThat(jdbc.queryForList("SELECT DISTINCT avatar_url FROM users",String.class))
                .containsExactlyInAnyOrderElementsOf(ProfilePresetCatalog.catalog().avatars().stream().map(ProfilePresetCatalog.Preset::url).toList());
        assertThat(jdbc.queryForList("SELECT DISTINCT banner_url FROM users",String.class))
                .containsExactlyInAnyOrderElementsOf(ProfilePresetCatalog.catalog().banners().stream().map(ProfilePresetCatalog.Preset::url).toList());
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT avatar_url,banner_url) FROM users",Long.class))
                .isEqualTo((long)ProfilePresetCatalog.catalog().avatars().size()*ProfilePresetCatalog.catalog().banners().size());
        for(var preset:java.util.stream.Stream.concat(ProfilePresetCatalog.catalog().avatars().stream(),ProfilePresetCatalog.catalog().banners().stream()).toList()) {
            assertThat(Files.isRegularFile(Path.of("../frontend/public"+preset.url()))).as(preset.url()).isTrue();
        }
    }
    @AfterEach void cleanup() {if(created)admin.execute("DROP DATABASE `"+database+"`");}
}
