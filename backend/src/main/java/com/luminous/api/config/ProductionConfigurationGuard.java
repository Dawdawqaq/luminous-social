package com.luminous.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.net.URI;

/** 生产配置错误在启动时报告，防止节点使用默认密钥或容器内部公开地址。 */
@Component
@Profile("prod")
public class ProductionConfigurationGuard {
    public ProductionConfigurationGuard(@Value("${app.jwt.secret}") String secret,
            @Value("${app.public-base-url}") String publicOrigin, @Value("${app.storage.minio.public-endpoint}") String fileOrigin,
            @Value("${app.security.secure-cookies:false}") boolean secureCookies) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32 || (secret.contains("change-this-development") || secret.startsWith("replace-"))) {
            throw new IllegalArgumentException("生产 JWT 密钥必须显式配置且至少三十二字节");
        }
        for (String value : java.util.List.of(publicOrigin, fileOrigin)) {
            URI uri = URI.create(com.luminous.api.storage.FileAccessTokens.normalizeOrigin(value));
            if (java.util.List.of("minio", "backend", "backend-2").contains(uri.getHost().toLowerCase(java.util.Locale.ROOT))) {
                throw new IllegalArgumentException("公开文件地址不能使用容器内部主机名");
            }
            if (secureCookies && !"https".equals(uri.getScheme())) { throw new IllegalArgumentException("安全 Cookie 模式必须配置 HTTPS 公开地址"); }
        }
    }
}
