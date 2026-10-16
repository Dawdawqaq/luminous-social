package com.luminous.api.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

/** 对照设计定稿与发布资源，避免目录混入未选头像或指向缺失图片。 */
class ProfilePresetCatalogTest {
    @Test
    void avatarCatalogMatchesApprovedEditionAndPublishedFiles() throws Exception {
        var manifest = new ObjectMapper().readTree(Path.of("../assets/characters/avatar-cos-v1/approved-v1.json").toFile());
        var approved = new java.util.ArrayList<String>();
        for (var avatar : manifest.get("avatars")) approved.add(avatar.get("id").asText());
        assertThat(ProfilePresetCatalog.catalog().avatars()).extracting(ProfilePresetCatalog.Preset::id)
                .containsExactlyElementsOf(approved).doesNotHaveDuplicates();
        for (var avatar : ProfilePresetCatalog.catalog().avatars()) {
            var asset = Path.of("../frontend/public" + avatar.url());
            assertThat(Files.size(asset)).as(avatar.id()).isBetween(1000L, 100000L);
            byte[] signature = Files.readAllBytes(asset);
            assertThat(new String(signature, 8, 4, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("WEBP");
        }
    }
}
