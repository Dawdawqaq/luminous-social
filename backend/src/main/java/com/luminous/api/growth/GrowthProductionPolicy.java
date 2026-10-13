package com.luminous.api.growth;

import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/** 生产启用必须明确标记为正式规则，避免开发草案随环境变量进入运营。 */
@Component
public class GrowthProductionPolicy {
    private final GrowthProperties properties;
    private final Environment environment;
    public GrowthProductionPolicy(GrowthProperties properties, Environment environment) { this.properties = properties; this.environment = environment; }
    @PostConstruct
    public void validate() {
        if (environment.acceptsProfiles(Profiles.of("prod")) && properties.isEnabled() && properties.isProvisional()) {
            throw new IllegalStateException("生产成长奖励必须配置已确认的正式规则，不能使用参考草案");
        }
    }
}
