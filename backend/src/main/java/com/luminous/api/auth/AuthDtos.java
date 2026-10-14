package com.luminous.api.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank 
            @Size(min = 3, max = 32, message = "用户ID长度必须在 3-32 位之间")
            @jakarta.validation.constraints.Pattern(
                regexp = "^[a-zA-Z0-9_]+$", 
                message = "用户ID只能包含字母、数字和下划线"
            )
            String username,
            
            @NotBlank @Size(min = 2, max = 96) String displayName,
            @NotBlank @Size(min = 6, max = 72, message = "密码长度不能少于 6 位") String password
    ) {
    }

    public record UpdateProfileRequest(
            @Size(min = 2, max = 96) String displayName,
            String bio,
            String avatar,
            String banner
    ) {
    }

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password
    ) {
    }

    public record AuthResponse(String token, UserDto user) {
    }

    public record UserDto(
            String id,
            String username,
            String displayName,
            String avatar,
            String banner,
            String bio,
            Stats stats,
            String joinDate,
            Boolean isFollowing,
            String role,
            Boolean isBanned,
            com.luminous.api.growth.GrowthDtos.Progress growth
    ) {
    }

    public record Stats(long posts, long followers, long following) {
    }

    public record FollowUserDto(String id, String username, String displayName, String avatar, String bio, boolean isFollowing) {
        public FollowUserDto(String id,String username,String displayName,String avatar,String bio) {
            this(id,username,displayName,avatar,bio,false);
        }
    }
}
