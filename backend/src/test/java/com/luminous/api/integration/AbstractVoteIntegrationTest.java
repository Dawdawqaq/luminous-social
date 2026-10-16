package com.luminous.api.integration;

import com.luminous.api.auth.AuthDtos;
import com.luminous.api.auth.AuthService;
import com.luminous.api.auth.UserMapper;
import com.luminous.api.cache.CacheConstants;
import com.luminous.api.community.Community;
import com.luminous.api.community.CommunityMapper;
import com.luminous.api.common.SnowflakeIdGenerator;
import com.luminous.api.post.PostDtos;
import com.luminous.api.post.PostService;
import com.luminous.api.security.CurrentUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 使用真实 Redis 脚本与 H2 数据访问验证状态一致性及 HTTP 权限边界。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("vote-validation")
abstract class AbstractVoteIntegrationTest {
    @Autowired protected AuthService authService;
    @Autowired protected PostService postService;
    @Autowired protected CommunityMapper communityMapper;
    @Autowired protected UserMapper userMapper;
    @Autowired protected StringRedisTemplate redisTemplate;
    @Autowired protected JdbcTemplate jdbcTemplate;
    @Autowired protected TestRestTemplate restTemplate;

    protected final List<Long> userIds = new ArrayList<>();
    protected final List<Long> postIds = new ArrayList<>();
    protected String communitySlug;
    protected Long communityId;
    protected CurrentUser author;
    protected CurrentUser voter;
    protected String authorToken;
    protected String voterToken;
    protected String adminToken;

    @BeforeEach
    void setUp() {
        AuthDtos.AuthResponse adminAuth = register("admin");
        jdbcTemplate.update("UPDATE users SET role='ADMIN' WHERE id=?", Long.parseLong(adminAuth.user().id()));
        // 重新登录使 JWT 中的角色与数据库保持一致。
        adminToken = authService.login(new AuthDtos.LoginRequest(adminAuth.user().username(), "Password123!")).token();
        AuthDtos.AuthResponse authorAuth = register("author");
        AuthDtos.AuthResponse voterAuth = register("voter");
        author = new CurrentUser(Long.parseLong(authorAuth.user().id()), authorAuth.user().username(), "USER");
        voter = new CurrentUser(Long.parseLong(voterAuth.user().id()), voterAuth.user().username(), "USER");
        authorToken = authorAuth.token();
        voterToken = voterAuth.token();
        Community community = new Community();
        communityId = SnowflakeIdGenerator.getNextId();
        communitySlug = "e2e-vote-" + UUID.randomUUID().toString().substring(0, 8);
        community.setId(communityId);
        community.setSlug(communitySlug);
        community.setName("[E2E_TEST] 投票验收社区");
        community.setDescription("[E2E_TEST] 隔离链路验收");
        community.setMembersCount(0);
        community.setMembersUnit("members");
        community.setRulesJson("[]");
        community.setModeratorsJson("[]");
        communityMapper.insert(community);
    }

    protected AuthDtos.AuthResponse register(String suffix) {
        String username = "E2E_TEST_" + suffix + "_" + UUID.randomUUID().toString().substring(0, 8);
        AuthDtos.AuthResponse response = authService.register(
                new AuthDtos.RegisterRequest(username, "[E2E_TEST] " + suffix, "Password123!"));
        userIds.add(Long.parseLong(response.user().id()));
        return response;
    }

    protected Long createPost(boolean isPublic) {
        PostDtos.PostDto response = postService.create(new PostDtos.CreatePostRequest(
                communitySlug, "[E2E_TEST] 投票与权限", "[E2E_TEST] 内容", null, null, isPublic), author);
        Long postId = Long.parseLong(response.id());
        postIds.add(postId);
        return postId;
    }

    protected void assertStatus(String path, HttpMethod method, Object body, String token, HttpStatus expected) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        assertThat(restTemplate.exchange(path, method, new HttpEntity<>(body, headers), String.class).getStatusCode())
                .isEqualTo(expected);
    }

    @AfterEach
    void cleanTestData() {
        for (Long postId : postIds) {
            cleanEvents("post_id", postId);
            jdbcTemplate.update("DELETE FROM posts WHERE id=?", postId);
            redisTemplate.delete(List.of(CacheConstants.POST_DETAIL_KEY + postId,
                    CacheConstants.POST_UV_KEY + postId, "luminous:stat:post:votes:" + postId,
                    "luminous:vote:state:post:" + postId, "luminous:vote:version:post:" + postId));
            for (Long userId : userIds) {
                redisTemplate.delete("luminous:vote:post:" + postId + ":" + userId);
            }
        }
        if (communityId != null) {
            communityMapper.deleteById(communityId);
        }
        for (Long userId : userIds) {
            jdbcTemplate.update("DELETE FROM notification_like_receipts WHERE receiver_id=? OR actor_id=?", userId, userId);
            jdbcTemplate.update("DELETE FROM notifications WHERE receiver_id=? OR actor_id=?", userId, userId);
            cleanEvents("user_id", userId);
            userMapper.deleteById(userId);
            redisTemplate.delete(CacheConstants.USER_STATS_KEY + userId);
            redisTemplate.delete(CacheConstants.FEED_INBOX_KEY + userId);
            for (String month : List.of("202609", "202610")) {
                redisTemplate.delete(CacheConstants.CHECKIN_KEY + communitySlug + ":" + userId + ":" + month);
            }
        }
        postIds.clear();
        userIds.clear();
    }

    protected void cleanEvents(String column, long id) {
        if (!List.of("post_id", "user_id").contains(column)) { throw new IllegalArgumentException("清理字段无效"); }
        jdbcTemplate.update("DELETE FROM consumed_events WHERE event_id IN (SELECT event_id FROM reliable_events WHERE " + column + "=?)", id);
        jdbcTemplate.update("DELETE FROM failed_messages WHERE event_id IN (SELECT event_id FROM reliable_events WHERE " + column + "=?)", id);
        jdbcTemplate.update("DELETE FROM reliable_events WHERE " + column + "=?", id);
    }
}
