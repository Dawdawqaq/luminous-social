package com.luminous.api.integration;

import com.luminous.api.cache.*;
import com.luminous.api.common.*;
import com.luminous.api.vote.*;
import com.luminous.api.notification.*;
import com.luminous.api.post.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import java.util.*;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 真实 HLL、HTTP 边界及聚合事务，验证旧版逐帖往返和错误分页会违反这些断言。 */
class ListPaginationBatchIntegrationTest extends AbstractVoteIntegrationTest {
    @Autowired PostReadMetricsService metrics;
    @Autowired VoteCommandService commands;
    @Autowired VoteTransactionService transactions;
    @Autowired NotificationService notifications;
    @MockitoSpyBean PostMapper posts;
    @MockitoSpyBean org.springframework.data.redis.core.StringRedisTemplate redis;
    @AfterEach void resetSpies() { reset(posts,redis); }
    private List<Long> seedPosts(int count) {
        List<Long> ids=new ArrayList<>(); LocalDateTime timestamp=LocalDateTime.now().minusMinutes(2);
        for(int i=0;i<count;i++) { long id=SnowflakeIdGenerator.getNextId();ids.add(id);postIds.add(id); }
        jdbcTemplate.batchUpdate("INSERT INTO posts(id,author_id,community_id,title,created_at,is_public) VALUES(?,?,?,?,?,TRUE)",ids,100,
                (statement,id)->{statement.setLong(1,id);statement.setLong(2,author.id());statement.setLong(3,communityId);
                    statement.setString(4,"[E2E_TEST] 列表窗口");statement.setObject(5,timestamp);});
        return ids;
    }
    @Test void fiftyDtosUseOneUvPipelineAndBatchedAuthoritativeVoteState() {
        List<Long> ids=seedPosts(50);
        for(Long id:ids) { redis.opsForHyperLogLog().add(CacheConstants.POST_UV_KEY+id,"[E2E_TEST] 同一访客"); }
        var event=commands.accept(ids.getFirst(),voter.id(),-1).message();
        clearInvocations(redis);
        var dtos=postService.assemblePostDtos(posts.selectBatchIds(ids),voter);
        assertThat(dtos).hasSize(50);assertThat(dtos).allMatch(dto->dto.viewCount()==1);
        assertThat(dtos.stream().filter(dto->dto.id().equals(String.valueOf(ids.getFirst()))).findFirst().orElseThrow().userVote()).isEqualTo(-1);
        verify(redis,times(1)).executePipelined(any(org.springframework.data.redis.core.RedisCallback.class));
        assertThat(jdbcTemplate.queryForObject("SELECT votes FROM posts WHERE id=?",Integer.class,ids.getFirst())).isZero();
        transactions.process(event);
    }
    @Test void independentUvKeysDoNotReturnTheUnionAndMissingKeysReturnZero() {
        List<Long> ids=seedPosts(3);
        redis.opsForHyperLogLog().add(CacheConstants.POST_UV_KEY+ids.get(0),"[E2E_TEST] A");
        redis.opsForHyperLogLog().add(CacheConstants.POST_UV_KEY+ids.get(1),"[E2E_TEST] B","[E2E_TEST] C");
        assertThat(metrics.uvCounts(ids)).containsEntry(ids.get(0),1L).containsEntry(ids.get(1),2L).containsEntry(ids.get(2),0L);
    }
    @Test void fiveHundredWindowIsStableForTimestampTiesAndLastPageStops() {
        var ids=seedPosts(620);List<String> seen=new ArrayList<>();
        for(int page=1;page<=7;page++) {
            var response=postService.list("new",communitySlug,null,page,80,voter);
            assertThat(response.total()).isEqualTo(500);assertThat(response.hasNext()).isEqualTo(page<7);
            assertThat(response.items()).hasSize(page==7?20:80);response.items().forEach(item->seen.add(item.id()));
        }
        var expected=ids.reversed().subList(0,500).stream().map(String::valueOf).toList();
        assertThat(seen).containsExactlyElementsOf(expected);assertThat(new HashSet<>(seen)).hasSize(500);
        assertStatus("/api/posts?page=8&size=80",HttpMethod.GET,null,null,HttpStatus.BAD_REQUEST);
    }
    @Test void malformedAndOversizedHttpPagesAreRejectedBeforeDatabaseAccess() {
        for(String query:List.of("page=0","page=-1","page=abc","page=9223372036854775808","size=0","size=101","page=9223372036854775807&size=100")) {
            assertStatus("/api/posts?"+query,HttpMethod.GET,null,null,HttpStatus.BAD_REQUEST);
            assertStatus("/api/bookmarks?"+query,HttpMethod.GET,null,voterToken,HttpStatus.BAD_REQUEST);
            assertStatus("/api/notifications?"+query,HttpMethod.GET,null,voterToken,HttpStatus.BAD_REQUEST);
        }
        assertStatus("/api/feed?offset=10001&lastScore=1",HttpMethod.GET,null,voterToken,HttpStatus.BAD_REQUEST);
    }
    @Test void aggregateLatestDesiredStatesSkipDuplicatesAndOldMessagesThenCancelCorrectly() {
        long id=createPost(true);
        var like=commands.accept(id,voter.id(),1).message();var cancel=commands.accept(id,voter.id(),0).message();
        var authorLike=commands.accept(id,author.id(),1).message();
        var result=transactions.processBatch(List.of(cancel,like,authorLike,authorLike));
        assertThat(result.scoreUpdates()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT votes FROM posts WHERE id=?",Integer.class,id)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM consumed_events WHERE event_id IN (?,?,?)",Integer.class,cancel.msgId(),like.msgId(),authorLike.msgId())).isEqualTo(3);
        transactions.processBatch(List.of(commands.accept(id,author.id(),0).message(),commands.accept(id,voter.id(),-1).message()));
        assertThat(jdbcTemplate.queryForObject("SELECT votes FROM posts WHERE id=?",Integer.class,id)).isEqualTo(-1);
    }
    @Test void aggregateCounterFailureRollsBackEveryBallotWatermarkAndConsumption() {
        long id=createPost(true);var first=commands.accept(id,author.id(),1).message();var second=commands.accept(id,voter.id(),1).message();
        doThrow(new IllegalStateException("[E2E_TEST] 聚合总分更新失败")).when(posts).updateVotesAtomic(id,2);
        assertThatThrownBy(()->transactions.processBatch(List.of(first,second))).isInstanceOf(IllegalStateException.class);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM post_votes WHERE post_id=?",Integer.class,id)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM consumed_events WHERE event_id IN (?,?)",Integer.class,first.msgId(),second.msgId())).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT SUM(applied_version) FROM vote_states WHERE post_id=?",Long.class,id)).isZero();
        reset(posts);transactions.processBatch(List.of(first,second));
        assertThat(jdbcTemplate.queryForObject("SELECT votes FROM posts WHERE id=?",Integer.class,id)).isEqualTo(2);
        verify(posts,times(1)).updateVotesAtomic(id,2);
    }
    @Test void notificationCommitAndReadOperationsReturnCurrentUnreadCounts() {
        notifications.handleEvent(new NotificationEvent(author.id(),voter.id(),"COMMENT_POST","USER",voter.id(),"[E2E_TEST] 未读刷新"));
        assertThat(notifications.getUnreadCount(voter).unreadCount()).isEqualTo(1);
        Long id=jdbcTemplate.queryForObject("SELECT id FROM notifications WHERE receiver_id=?",Long.class,voter.id());
        assertStatus("/api/notifications/"+id+"/read",HttpMethod.PUT,null,voterToken,HttpStatus.OK);
        assertThat(notifications.getUnreadCount(voter).unreadCount()).isZero();
        notifications.handleEvent(new NotificationEvent(author.id(),voter.id(),"FOLLOW_USER","USER",voter.id(),"[E2E_TEST] 新通知"));
        assertThat(notifications.getUnreadCount(voter).unreadCount()).isEqualTo(1);
        assertStatus("/api/notifications/read-all",HttpMethod.PUT,null,voterToken,HttpStatus.OK);
        assertThat(notifications.getUnreadCount(voter).unreadCount()).isZero();
    }
}
