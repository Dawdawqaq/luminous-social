package com.luminous.api.growth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;

public final class GrowthDtos {
    private GrowthDtos() {}
    public record Progress(long experience, int level, long levelStartExperience, Long nextLevelExperience,
                           long experienceInLevel, long experienceToNextLevel, double progressPercent,
                           boolean maxLevel, int badgeStage, String ruleVersion, boolean provisional) {}
    public record Overview(Progress personal, Progress community, String communitySlug,
                           boolean rewardsEnabled, String ruleVersion, boolean provisional) {}
    public record LedgerEntry(String id, String rewardId, String sourceKey, String communityId, String action,
                              String contentType, String contentId, LocalDate rewardDate, long delta,
                              String operation, String reason, String ruleVersion, Instant createdAt) {}
    public record ReadSession(String token, Instant readyAt, Instant expiresAt, String ruleVersion, boolean provisional) {}
    public record ReadResult(boolean firstQualifiedRead, long experienceAwarded) {}
    public record ModerationRequest(@NotBlank @Size(max = 255) String reason) {}
}
