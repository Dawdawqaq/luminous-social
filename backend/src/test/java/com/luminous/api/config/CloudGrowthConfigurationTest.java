package com.luminous.api.config;

import com.luminous.api.growth.GrowthProductionPolicy;
import com.luminous.api.growth.GrowthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/** 加载真实云规则文件，验证生产覆盖顺序及草案保护，不连接数据库。 */
class CloudGrowthConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(GrowthConfiguration.class)
            .withPropertyValues("spring.profiles.active=prod");

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(GrowthProperties.class)
    @Import(GrowthProductionPolicy.class)
    static class GrowthConfiguration {}

    @Test
    void actualCloudFileOverridesProductionDefaultsAndBindsAllRewardActions() {
        context.withPropertyValues("spring.config.additional-location=file:../deploy/cloud/growth-production.yml")
                .run(loaded -> {
                    assertThat(loaded).hasNotFailed();
                    GrowthProperties rules = loaded.getBean(GrowthProperties.class);
                    assertThat(rules.isEnabled()).isTrue();
                    assertThat(rules.isProvisional()).isFalse();
                    assertThat(rules.isReadEnabled()).isFalse();
                    assertThat(rules.getRuleVersion()).isEqualTo("prod-20261016-v1");
                    assertThat(rules.getRewards()).containsOnlyKeys(GrowthProperties.Action.values());
                    assertThat(rules.getRewards().get(GrowthProperties.Action.CHECK_IN))
                            .isEqualTo(new GrowthProperties.RewardRule(5, 15));
                    assertThat(rules.getRewards().get(GrowthProperties.Action.POST))
                            .isEqualTo(new GrowthProperties.RewardRule(15, 30));
                    assertThat(rules.getRewards().get(GrowthProperties.Action.COMMENT))
                            .isEqualTo(new GrowthProperties.RewardRule(3, 15));
                    assertThat(rules.getRewards().get(GrowthProperties.Action.LIKE))
                            .isEqualTo(new GrowthProperties.RewardRule(2, 500));
                    assertThat(rules.getRewards().get(GrowthProperties.Action.READ))
                            .isEqualTo(new GrowthProperties.RewardRule(1, 100));
                    assertThat(rules.getThresholds()).hasSize(18).startsWith(0L).endsWith(300000L);
                });
    }

    @Test
    void productionRemainsDisabledWithoutExplicitCloudFile() {
        context.run(loaded -> {
            assertThat(loaded).hasNotFailed();
            assertThat(loaded.getBean(GrowthProperties.class).isEnabled()).isFalse();
        });
    }

    @Test
    void provisionalOverrideStillPreventsProductionStartup() {
        context.withPropertyValues("spring.config.additional-location=file:../deploy/cloud/growth-production.yml",
                        "app.growth.provisional=true")
                .run(loaded -> assertThat(loaded).hasFailed().getFailure()
                        .hasRootCauseMessage("生产成长奖励必须配置已确认的正式规则，不能使用参考草案"));
    }
}
