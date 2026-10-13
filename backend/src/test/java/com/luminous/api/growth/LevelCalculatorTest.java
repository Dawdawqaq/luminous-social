package com.luminous.api.growth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.*;

class LevelCalculatorTest {
    private final GrowthProperties properties = new GrowthProperties();
    private final LevelCalculator calculator = new LevelCalculator(properties);

    @ParameterizedTest
    @CsvSource({"0,1,1", "4,1,1", "5,2,1", "49,4,1", "50,5,2", "620,8,2", "3000,11,3",
            "10000,13,4", "60000,16,5", "100000,17,5", "299999,17,5", "300000,18,6", "9223372036854775807,18,6"})
    void calculatesThresholdsAndVisualStages(long experience, int level, int stage) {
        var progress = calculator.calculate(experience);
        assertThat(progress.level()).isEqualTo(level);
        assertThat(progress.badgeStage()).isEqualTo(stage);
        assertThat(progress.provisional()).isTrue();
    }

    @Test void calculatesProgressWithoutInventingNextLevelAtCap() {
        var progress = calculator.calculate(620);
        assertThat(progress.experienceInLevel()).isEqualTo(120);
        assertThat(progress.experienceToNextLevel()).isEqualTo(380);
        assertThat(progress.progressPercent()).isEqualTo(24);
        var maximum = calculator.calculate(300001);
        assertThat(maximum.maxLevel()).isTrue();
        assertThat(maximum.nextLevelExperience()).isNull();
        assertThat(maximum.experienceToNextLevel()).isZero();
        assertThatThrownBy(() -> calculator.calculate(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void everyLevelBoundaryAndDowngradeUsesSameCurve() {
        for (int index = 1; index < 18; index++) {
            long threshold = properties.getThresholds().get(index);
            assertThat(calculator.calculate(threshold).level()).isEqualTo(index + 1);
            assertThat(calculator.calculate(threshold - 1).level()).isEqualTo(index);
        }
    }

    @Test void enabledRulesCannotOmitRewardsOrAcceptDraftInProduction() {
        properties.setEnabled(true);
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalArgumentException.class);
        var environment = new StandardEnvironment();
        environment.setActiveProfiles("prod");
        assertThatThrownBy(() -> new GrowthProductionPolicy(properties, environment).validate()).isInstanceOf(IllegalStateException.class);
    }
}
