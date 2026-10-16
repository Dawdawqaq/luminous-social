package com.luminous.api.community;

import java.util.List;
import jakarta.validation.constraints.*;

public final class CommunityDtos {
    private CommunityDtos() {
    }

    public record CommunityDto(
            String id,
            String name,
            String displayName,
            String icon,
            String banner,
            String description,
            int membersCount,
            String membersUnit,
            List<String> rules,
            List<ModeratorDto> moderators,
            boolean joined,
            String ownerId,
            String category
    ) {
    }

    public record CreateCommunityRequest(
            @NotBlank @Pattern(regexp = "[a-z0-9_-]{1,64}", message = "短名称限1至64位小写字母、数字、下划线和短横线") String slug,
            @NotBlank @Size(max = 96) String name,
            @Pattern(regexp = "Philosophy|Craft|Aesthetics|Living|Systems|Literature") String category,
            @Size(max = 1024) String description
    ) {
    }

    public record UpdateCommunityRequest(
            @NotBlank @Size(max = 96) String name,
            @Size(max = 512) String iconUrl,
            @Size(max = 512) String bannerUrl,
            @Size(max = 1024) String description,
            @Size(max = 10) List<@NotBlank @Size(max = 200) String> rules
    ) {
    }

    public record ModeratorDto(String id, String username, String displayName, String initials) {
    }

    public record RecommendedCommunityDto(String id, String name, String members, String category, String icon, boolean isJoined) {
    }
}
