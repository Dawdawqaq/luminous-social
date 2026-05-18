package com.luminous.api.community;

import java.util.List;

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
            String ownerId
    ) {
    }

    public record CreateCommunityRequest(
            String slug,
            String name,
            String category,
            String description
    ) {
    }

    public record UpdateCommunityRequest(
            String name,
            String iconUrl,
            String bannerUrl,
            String description,
            List<String> rules
    ) {
    }

    public record ModeratorDto(String id, String username, String displayName, String initials) {
    }

    public record RecommendedCommunityDto(String id, String name, String members, String category, String icon, boolean isJoined) {
    }
}
