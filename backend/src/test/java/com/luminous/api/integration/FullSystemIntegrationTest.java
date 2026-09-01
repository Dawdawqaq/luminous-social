package com.luminous.api.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luminous.api.auth.AuthDtos;
import com.luminous.api.checkin.CheckInDtos;
import com.luminous.api.common.ApiResponse;
import com.luminous.api.community.CommunityDtos;
import com.luminous.api.feed.FeedDtos;
import com.luminous.api.post.PostDtos;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class FullSystemIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private static String tokenUserA;
    private static String tokenUserB;
    private static String communitySlug = "tech-infra-" + UUID.randomUUID().toString().substring(0, 6);
    private static String createdPostId;

    private String getBaseUrl() {
        return "http://localhost:" + port + "/api";
    }

    private HttpHeaders createAuthHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return headers;
    }

    @Test
    @Order(1)
    void test01_UserRegistrationAndLogin() {
        String usernameA = "author_" + UUID.randomUUID().toString().substring(0, 6);
        String usernameB = "follower_" + UUID.randomUUID().toString().substring(0, 6);

        // Register User A
        AuthDtos.RegisterRequest reqA = new AuthDtos.RegisterRequest(usernameA, "Author A", "Password123!");
        ResponseEntity<ApiResponse<AuthDtos.AuthResponse>> respA = restTemplate.exchange(
                getBaseUrl() + "/auth/register",
                HttpMethod.POST,
                new HttpEntity<>(reqA),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(respA.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respA.getBody()).isNotNull();
        assertThat(respA.getBody().success()).isTrue();
        tokenUserA = respA.getBody().data().token();
        assertThat(tokenUserA).isNotBlank();

        // Register User B
        AuthDtos.RegisterRequest reqB = new AuthDtos.RegisterRequest(usernameB, "Follower B", "Password123!");
        ResponseEntity<ApiResponse<AuthDtos.AuthResponse>> respB = restTemplate.exchange(
                getBaseUrl() + "/auth/register",
                HttpMethod.POST,
                new HttpEntity<>(reqB),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(respB.getStatusCode()).isEqualTo(HttpStatus.OK);
        tokenUserB = respB.getBody().data().token();

        // Follow User A by User B
        HttpHeaders headersB = createAuthHeaders(tokenUserB);
        ResponseEntity<ApiResponse<Object>> followResp = restTemplate.exchange(
                getBaseUrl() + "/users/" + usernameA + "/follow",
                HttpMethod.POST,
                new HttpEntity<>(headersB),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(followResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @Order(2)
    void test02_CommunityCreationAndJoining() {
        HttpHeaders headersA = createAuthHeaders(tokenUserA);
        CommunityDtos.CreateCommunityRequest commReq = new CommunityDtos.CreateCommunityRequest(
                communitySlug, "Tech Infrastructure", "Engineering", "Discussion on distributed systems"
        );

        ResponseEntity<ApiResponse<CommunityDtos.CommunityDto>> createResp = restTemplate.exchange(
                getBaseUrl() + "/communities",
                HttpMethod.POST,
                new HttpEntity<>(commReq, headersA),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(createResp.getBody().data().name()).isEqualTo(communitySlug);

        // User B joins community
        HttpHeaders headersB = createAuthHeaders(tokenUserB);
        ResponseEntity<ApiResponse<CommunityDtos.CommunityDto>> joinResp = restTemplate.exchange(
                getBaseUrl() + "/communities/" + communitySlug + "/join",
                HttpMethod.POST,
                new HttpEntity<>(headersB),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(joinResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(joinResp.getBody().data().joined()).isTrue();
    }

    @Test
    @Order(3)
    void test03_CommunityCheckInWithRedisBitmap() {
        HttpHeaders headersB = createAuthHeaders(tokenUserB);

        // 1st Check-in -> Success
        ResponseEntity<ApiResponse<CheckInDtos.CheckInResponse>> checkInResp = restTemplate.exchange(
                getBaseUrl() + "/checkin/" + communitySlug,
                HttpMethod.POST,
                new HttpEntity<>(headersB),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(checkInResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(checkInResp.getBody().data().success()).isTrue();
        assertThat(checkInResp.getBody().data().consecutiveDays()).isGreaterThanOrEqualTo(1);

        // 2nd Check-in on same day -> Already checked in
        ResponseEntity<ApiResponse<CheckInDtos.CheckInResponse>> duplicateResp = restTemplate.exchange(
                getBaseUrl() + "/checkin/" + communitySlug,
                HttpMethod.POST,
                new HttpEntity<>(headersB),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(duplicateResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(duplicateResp.getBody().data().success()).isFalse();
        assertThat(duplicateResp.getBody().data().message()).isEqualTo("今日已签到");

        // Query Stats
        ResponseEntity<ApiResponse<CheckInDtos.CheckInStats>> statsResp = restTemplate.exchange(
                getBaseUrl() + "/checkin/" + communitySlug + "/stats",
                HttpMethod.GET,
                new HttpEntity<>(headersB),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(statsResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(statsResp.getBody().data().checkedToday()).isTrue();
        assertThat(statsResp.getBody().data().totalDays()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @Order(4)
    void test04_PostCreationAndFeedFanOut() throws Exception {
        HttpHeaders headersA = createAuthHeaders(tokenUserA);
        PostDtos.CreatePostRequest postReq = new PostDtos.CreatePostRequest(
                communitySlug, "Distributed Cache Best Practices", "Deep dive into L1/L2 caching with Redis & Caffeine", null, true
        );

        ResponseEntity<ApiResponse<PostDtos.PostDto>> postResp = restTemplate.exchange(
                getBaseUrl() + "/posts",
                HttpMethod.POST,
                new HttpEntity<>(postReq, headersA),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(postResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        createdPostId = postResp.getBody().data().id();
        assertThat(createdPostId).isNotBlank();

        // Allow async / pipeline fanout to settle
        Thread.sleep(200);

        // User B reads Feed
        HttpHeaders headersB = createAuthHeaders(tokenUserB);
        ResponseEntity<ApiResponse<FeedDtos.ScrollResult<PostDtos.PostDto>>> feedResp = restTemplate.exchange(
                getBaseUrl() + "/feed",
                HttpMethod.GET,
                new HttpEntity<>(headersB),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(feedResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(feedResp.getBody().data().items()).isNotEmpty();
        assertThat(feedResp.getBody().data().items().stream().anyMatch(p -> p.id().equals(createdPostId))).isTrue();
    }

    @Test
    @Order(5)
    void test05_PostDetailAndHyperLogLogUV() {
        HttpHeaders headersB = createAuthHeaders(tokenUserB);

        // Fetch detail 1st time
        ResponseEntity<ApiResponse<PostDtos.PostDto>> detailResp1 = restTemplate.exchange(
                getBaseUrl() + "/posts/" + createdPostId,
                HttpMethod.GET,
                new HttpEntity<>(headersB),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(detailResp1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(detailResp1.getBody().data().viewCount()).isGreaterThanOrEqualTo(1L);

        // Fetch detail 2nd time with same user -> HyperLogLog counts unique visitor, count remains 1
        ResponseEntity<ApiResponse<PostDtos.PostDto>> detailResp2 = restTemplate.exchange(
                getBaseUrl() + "/posts/" + createdPostId,
                HttpMethod.GET,
                new HttpEntity<>(headersB),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(detailResp2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(detailResp2.getBody().data().viewCount()).isEqualTo(detailResp1.getBody().data().viewCount());
    }

    @Test
    @Order(6)
    void test06_IdempotentRequestInterception() {
        HttpHeaders headersA = createAuthHeaders(tokenUserA);
        String idempotencyKey = "TEST_TOKEN_" + UUID.randomUUID().toString();
        headersA.set("X-Idempotency-Key", idempotencyKey);

        PostDtos.CreateCommentRequest commentReq = new PostDtos.CreateCommentRequest("First atomic comment!");

        // First attempt with idempotency key -> Success (200 OK)
        ResponseEntity<ApiResponse<PostDtos.CommentDto>> firstResp = restTemplate.exchange(
                getBaseUrl() + "/posts/" + createdPostId + "/comments",
                HttpMethod.POST,
                new HttpEntity<>(commentReq, headersA),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(firstResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Second attempt with same idempotency key -> 409 Conflict
        ResponseEntity<ApiResponse<Object>> secondResp = restTemplate.exchange(
                getBaseUrl() + "/posts/" + createdPostId + "/comments",
                HttpMethod.POST,
                new HttpEntity<>(commentReq, headersA),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(secondResp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(secondResp.getBody().success()).isFalse();
        assertThat(secondResp.getBody().error().code()).isEqualTo("DUPLICATE_REQUEST");
    }
}
