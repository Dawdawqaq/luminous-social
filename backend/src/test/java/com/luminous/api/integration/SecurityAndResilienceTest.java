package com.luminous.api.integration;

import com.luminous.api.auth.AuthDtos;
import com.luminous.api.common.ApiResponse;
import com.luminous.api.common.PageResponse;
import com.luminous.api.community.CommunityDtos;
import com.luminous.api.post.PostDtos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SecurityAndResilienceTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private com.luminous.api.security.CaptchaService captchaService;

    @Autowired
    private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    private String tokenAdmin;
    private String tokenUserA;
    private String tokenUserB;
    private String userAPostId;

    private String getBaseUrl() {
        return "http://localhost:" + port + "/api";
    }

    private HttpHeaders createAuthHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Idempotency-Key", UUID.randomUUID().toString());
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return headers;
    }

    @BeforeAll
    void initUsers() {
        // Clear rate limit keys before test initialization
        redisTemplate.delete("luminous:ratelimit:auth:127.0.0.1");
        redisTemplate.delete("luminous:ratelimit:auth:0:0:0:0:0:0:0:1");

        // Register User A
        String uA = "sec_user_a_" + UUID.randomUUID().toString().substring(0, 6);
        AuthDtos.RegisterRequest reqA = new AuthDtos.RegisterRequest(uA, "User A", "SecPass123!");
        ResponseEntity<ApiResponse<AuthDtos.AuthResponse>> rA = restTemplate.exchange(
                getBaseUrl() + "/auth/register", HttpMethod.POST, new HttpEntity<>(reqA, AuthHttpTestSupport.proofHeaders(captchaService, redisTemplate, "register")), new ParameterizedTypeReference<>() {}
        );
        tokenUserA = rA.getBody().data().token();

        // Register User B
        String uB = "sec_user_b_" + UUID.randomUUID().toString().substring(0, 6);
        AuthDtos.RegisterRequest reqB = new AuthDtos.RegisterRequest(uB, "User B", "SecPass123!");
        ResponseEntity<ApiResponse<AuthDtos.AuthResponse>> rB = restTemplate.exchange(
                getBaseUrl() + "/auth/register", HttpMethod.POST, new HttpEntity<>(reqB, AuthHttpTestSupport.proofHeaders(captchaService, redisTemplate, "register")), new ParameterizedTypeReference<>() {}
        );
        tokenUserB = rB.getBody().data().token();

        // Create Community & Post by User A
        String slug = "sec-comm-" + UUID.randomUUID().toString().substring(0, 6);
        restTemplate.exchange(
                getBaseUrl() + "/communities", HttpMethod.POST,
                new HttpEntity<>(new CommunityDtos.CreateCommunityRequest(slug, "Sec Community", "General", "Security test"), createAuthHeaders(tokenUserA)),
                new ParameterizedTypeReference<>() {}
        );

        ResponseEntity<ApiResponse<PostDtos.PostDto>> postResp = restTemplate.exchange(
                getBaseUrl() + "/posts", HttpMethod.POST,
                new HttpEntity<>(new PostDtos.CreatePostRequest(slug, "Target Post for Security", "Content", null, null, true), createAuthHeaders(tokenUserA)),
                new ParameterizedTypeReference<>() {}
        );
        userAPostId = postResp.getBody().data().id();
    }

    @Test
    void testSqlInjectionSafety_OnSearchEndpoints() {
        String[] sqlInjectionPayloads = {
                "' OR '1'='1",
                "1; DROP TABLE users; --",
                "' UNION SELECT username, password_hash, role FROM users --",
                "admin' --",
                "\" OR \"\"=\""
        };

        for (String payload : sqlInjectionPayloads) {
            ResponseEntity<ApiResponse<PageResponse<PostDtos.PostDto>>> searchResp = restTemplate.exchange(
                    getBaseUrl() + "/posts?search=" + payload,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            // Must respond safely with 200 OK and valid JSON (no 500 SQL syntax crash, no full table exposure)
            assertThat(searchResp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(searchResp.getBody()).isNotNull();
            assertThat(searchResp.getBody().success()).isTrue();
        }
    }

    @Test
    void testHorizontalPrivilegeEscalation_UserBCannotDeleteUserAPost() {
        HttpHeaders headersUserB = createAuthHeaders(tokenUserB);

        // User B tries to DELETE User A's post
        ResponseEntity<ApiResponse<Object>> deleteResp = restTemplate.exchange(
                getBaseUrl() + "/posts/" + userAPostId,
                HttpMethod.DELETE,
                new HttpEntity<>(headersUserB),
                new ParameterizedTypeReference<>() {}
        );

        // Must reject with 403 Forbidden
        assertThat(deleteResp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(deleteResp.getBody().success()).isFalse();
    }

    @Test
    void testJwtTampering_RejectsInvalidSignature() {
        // Tampered token with invalid signature
        String tamperedToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMDAiLCJ1c2VybmFtZSI6ImFkbWluIiwicm9sZSI6IkFETUlOIn0.INVALID_SIGNATURE_HASH";
        HttpHeaders headers = createAuthHeaders(tamperedToken);

        ResponseEntity<ApiResponse<Object>> resp = restTemplate.exchange(
                getBaseUrl() + "/auth/me",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
        );

        // Must reject with 401/403 Unauthorized
        assertThat(resp.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    @Test
    void testSlidingWindowRateLimiter_BlocksBruteForceAttempts() {
        String testIpUsername = "ratelimit_target_" + UUID.randomUUID().toString().substring(0, 4);

        int maxAllowed = 60;
        int totalRequests = 65;
        int rateLimitedCount = 0;

        for (int i = 0; i < totalRequests; i++) {
            AuthDtos.LoginRequest req = new AuthDtos.LoginRequest(testIpUsername, "WrongPassword!");
            ResponseEntity<ApiResponse<Object>> loginResp = restTemplate.exchange(
                    getBaseUrl() + "/auth/login",
                    HttpMethod.POST,
                    new HttpEntity<>(req),
                    new ParameterizedTypeReference<>() {}
            );

            if (loginResp.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                rateLimitedCount++;
                assertThat(loginResp.getBody().error().code()).isEqualTo("RATE_LIMIT_EXCEEDED");
            }
        }

        // Verify that requests over the 60 threshold triggered 429 Too Many Requests
        assertThat(rateLimitedCount).isGreaterThanOrEqualTo(totalRequests - maxAllowed);
    }
}
