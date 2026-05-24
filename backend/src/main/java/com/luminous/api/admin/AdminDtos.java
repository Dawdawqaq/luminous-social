package com.luminous.api.admin;

public final class AdminDtos {
    private AdminDtos() {}

    public record AdminUserDto(
            String id,
            String username,
            String displayName,
            String avatar,
            String role,
            boolean isBanned,
            String joinDate
    ) {}
}
