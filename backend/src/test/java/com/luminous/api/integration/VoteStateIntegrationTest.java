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

/** 投票状态与权限回归。 */
class VoteStateIntegrationTest extends AbstractVoteIntegrationTest {
    @Test
    void voteTransitions_KeepRedisRecordDatabaseRecordAndScoreConsistent() {
        Long postId = createPost(true);
        // 依次覆盖首次、重复、取消以及两个方向的赞踩切换。
        for (int value : new int[]{1, 1, 0, -1, -1, 0, 1, -1, 1, 0}) {
            PostDtos.PostDto result = postService.votePost(postId, value, voter);
            assertThat(result.userVote()).isEqualTo(value);
            assertThat(result.votes()).isEqualTo(value);
            assertThat(redisTemplate.opsForValue().get("luminous:stat:post:votes:" + postId))
                    .isEqualTo(String.valueOf(value));
            assertThat(redisTemplate.opsForHash().get("luminous:vote:state:post:" + postId, String.valueOf(voter.id())))
                    .isEqualTo(String.valueOf(value));
            List<Integer> records = jdbcTemplate.queryForList(
                    "SELECT value FROM post_votes WHERE post_id=? AND user_id=?", Integer.class, postId, voter.id());
            assertThat(records).isEqualTo(value == 0 ? List.of() : List.of(value));
            assertThat(jdbcTemplate.queryForObject("SELECT votes FROM posts WHERE id=?", Integer.class, postId))
                    .isEqualTo(value);
        }
    }

    @Test
    void privatePost_HttpDeniesAnonymousAndOutsiderButAllowsAuthorAndAdmin() {
        Long postId = createPost(false);
        PostDtos.CommentDto comment = postService.addComment(postId,
                new PostDtos.CreateCommentRequest("[E2E_TEST] 私密评论"), author);
        for (String path : List.of("/api/posts/" + postId, "/api/posts/" + postId + "/comments")) {
            assertStatus(path, HttpMethod.GET, null, null, HttpStatus.NOT_FOUND);
            assertStatus(path, HttpMethod.GET, null, voterToken, HttpStatus.NOT_FOUND);
            assertStatus(path, HttpMethod.GET, null, authorToken, HttpStatus.OK);
            assertStatus(path, HttpMethod.GET, null, adminToken, HttpStatus.OK);
        }
        assertStatus("/api/posts/" + postId + "/vote", HttpMethod.POST,
                new PostDtos.VoteRequest(1), voterToken, HttpStatus.NOT_FOUND);
        assertStatus("/api/comments/" + comment.id() + "/vote", HttpMethod.POST,
                new PostDtos.VoteRequest(1), voterToken, HttpStatus.NOT_FOUND);
        assertStatus("/api/posts/" + postId + "/comments", HttpMethod.POST,
                new PostDtos.CreateCommentRequest("[E2E_TEST] 越权评论"), voterToken, HttpStatus.NOT_FOUND);
        assertStatus("/api/posts/" + postId + "/vote", HttpMethod.POST,
                new PostDtos.VoteRequest(1), null, HttpStatus.UNAUTHORIZED);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM post_votes WHERE post_id=?", Long.class, postId)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM comments WHERE post_id=?", Long.class, postId)).isEqualTo(1);
    }

}
