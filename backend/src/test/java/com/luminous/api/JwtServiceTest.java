package com.luminous.api;

import com.luminous.api.security.CurrentUser;
import com.luminous.api.security.JwtService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {
    @Test
    void createsAndParsesToken() {
        JwtService jwtService = new JwtService("test-secret-test-secret-test-secret-test-secret", 60);

        String token = jwtService.createToken(42L, "felix", "ADMIN");
        CurrentUser currentUser = jwtService.parse(token);

        assertThat(currentUser.id()).isEqualTo(42L);
        assertThat(currentUser.username()).isEqualTo("felix");
        assertThat(currentUser.role()).isEqualTo("ADMIN");
    }
}
