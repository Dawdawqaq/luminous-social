package com.luminous.api.growth;

import org.springframework.stereotype.Component;

/** 直接查累计门槛，升降级均重新计算；展示分段不参与经验计算。 */
@Component
public class LevelCalculator {
    private final GrowthProperties properties;
    public LevelCalculator(GrowthProperties properties) { this.properties = properties; }

    public GrowthDtos.Progress calculate(long experience) {
        if (experience < 0) throw new IllegalArgumentException("累计经验不能小于零");
        var thresholds = properties.getThresholds();
        int index = 0;
        while (index + 1 < thresholds.size() && experience >= thresholds.get(index + 1)) index++;
        int level = index + 1;
        boolean maxLevel = level == 18;
        long start = thresholds.get(index);
        Long next = maxLevel ? null : thresholds.get(index + 1);
        long within = experience - start;
        double percent = maxLevel ? 100 : within * 100.0 / (next - start);
        int stage = level <= 4 ? 1 : level <= 9 ? 2 : level <= 12 ? 3 : level <= 15 ? 4 : level <= 17 ? 5 : 6;
        return new GrowthDtos.Progress(experience, level, start, next, within, maxLevel ? 0 : next - experience,
                percent, maxLevel, stage, properties.getRuleVersion(), properties.isProvisional());
    }
}
