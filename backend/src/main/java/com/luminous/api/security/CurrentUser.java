package com.luminous.api.security;

public record CurrentUser(Long id, String username, String role) {
    /** 兼容旧调用处：无 role 时默认 USER */
    public CurrentUser(Long id, String username) {
        this(id, username, "USER");
    }
}

