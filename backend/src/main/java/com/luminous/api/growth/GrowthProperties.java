package com.luminous.api.growth;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** 默认不发奖励；参考门槛与奖励仅在显式启用的开发规则中使用。 */
@Component
@ConfigurationProperties(prefix = "app.growth")
public class GrowthProperties {
    public enum Action { CHECK_IN, POST, COMMENT, LIKE, READ }
    public record RewardRule(long amount, long dailyCap) {}
    private boolean enabled;
    private boolean provisional = true;
    private String ruleVersion = "reference-20261011";
    private List<Long> thresholds = List.of(0L, 5L, 15L, 30L, 50L, 100L, 200L, 500L, 1000L,
            2000L, 3000L, 6000L, 10000L, 18000L, 30000L, 60000L, 100000L, 300000L);
    private Map<Action, RewardRule> rewards = new EnumMap<>(Action.class);
    private int minimumPostCharacters = 12;
    private int minimumCommentCharacters = 4;
    private int rewardedCommentsPerPostPerDay = 2;
    private boolean readEnabled;
    private int readMinimumSeconds = 10;
    private int readSessionSeconds = 300;
    private int readersPerReward = 10;

    @PostConstruct
    public void validate() {
        if (thresholds == null || thresholds.size() != 18 || !Long.valueOf(0).equals(thresholds.getFirst())) {
            throw new IllegalArgumentException("成长门槛必须包含从零开始的十八级累计经验");
        }
        for (int index = 1; index < thresholds.size(); index++) {
            if (thresholds.get(index) == null || thresholds.get(index) <= thresholds.get(index - 1)) {
                throw new IllegalArgumentException("成长等级累计门槛必须严格递增");
            }
        }
        if (ruleVersion == null || ruleVersion.isBlank() || ruleVersion.length() > 64
                || minimumPostCharacters < 1 || minimumCommentCharacters < 1 || rewardedCommentsPerPostPerDay < 1
                || readMinimumSeconds < 1 || readSessionSeconds <= readMinimumSeconds || readersPerReward < 1) {
            throw new IllegalArgumentException("成长规则版本或有效贡献参数不合法");
        }
        for (Action action : Action.values()) {
            RewardRule rule = rewards.get(action);
            if (enabled && rule == null || rule != null && (rule.amount() < 0 || rule.dailyCap() < 0
                    || rule.amount() > rule.dailyCap())) {
                throw new IllegalArgumentException("启用成长必须显式配置各行为奖励及每日上限：" + action);
            }
        }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public boolean isProvisional() { return provisional; }
    public void setProvisional(boolean value) { provisional = value; }
    public String getRuleVersion() { return ruleVersion; }
    public void setRuleVersion(String value) { ruleVersion = value; }
    public List<Long> getThresholds() { return thresholds; }
    public void setThresholds(List<Long> value) { thresholds = value; }
    public Map<Action, RewardRule> getRewards() { return rewards; }
    public void setRewards(Map<Action, RewardRule> value) { rewards = value; }
    public int getMinimumPostCharacters() { return minimumPostCharacters; }
    public void setMinimumPostCharacters(int value) { minimumPostCharacters = value; }
    public int getMinimumCommentCharacters() { return minimumCommentCharacters; }
    public void setMinimumCommentCharacters(int value) { minimumCommentCharacters = value; }
    public int getRewardedCommentsPerPostPerDay() { return rewardedCommentsPerPostPerDay; }
    public void setRewardedCommentsPerPostPerDay(int value) { rewardedCommentsPerPostPerDay = value; }
    public boolean isReadEnabled() { return readEnabled; }
    public void setReadEnabled(boolean value) { readEnabled = value; }
    public int getReadMinimumSeconds() { return readMinimumSeconds; }
    public void setReadMinimumSeconds(int value) { readMinimumSeconds = value; }
    public int getReadSessionSeconds() { return readSessionSeconds; }
    public void setReadSessionSeconds(int value) { readSessionSeconds = value; }
    public int getReadersPerReward() { return readersPerReward; }
    public void setReadersPerReward(int value) { readersPerReward = value; }
}
