package com.luminous.api.integration;

import com.luminous.api.auth.FollowRateLimitException;
import com.luminous.api.mq.NotificationJournalService;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 验证业务事务、缓存失效和通知意图的真实集成；数据库为 H2，Redis 为独立开发实例。 */
class FollowInteractionIntegrationTest extends AbstractVoteIntegrationTest {
    @MockitoSpyBean private NotificationJournalService journal;

    private long actions() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM follow_actions WHERE user_id=?",Long.class,voter.id());
    }
    private void expireInterval() {
        // 仅推进本测试用户的 H2 限频记录，通知去重窗口保持原值。
        jdbcTemplate.update("UPDATE follow_action_gates SET last_changed_at=DATEADD('SECOND',-2,CURRENT_TIMESTAMP) WHERE user_id=?",voter.id());
    }
    @Test void repeatedDesiredStateDoesNotChangeStatisticsOrNotifyAgain() {
        assertThat(authService.follow(author.username(),voter).isFollowing()).isTrue();
        assertThat(authService.follow(author.username(),voter).isFollowing()).isTrue();
        assertThat(actions()).isEqualTo(1);
        assertThat(authService.currentUser(voter).stats().following()).isEqualTo(1);
        assertThat(authService.getUserProfile(author.username(),voter).stats().followers()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notifications WHERE receiver_id=? AND type='FOLLOW_USER'",Long.class,author.id())).isEqualTo(1);
        expireInterval();authService.unfollow(author.username(),voter);authService.unfollow(author.username(),voter);
        assertThat(actions()).isEqualTo(2);
        assertThat(authService.currentUser(voter).stats().following()).isZero();
        assertThat(authService.getUserProfile(author.username(),voter).stats().followers()).isZero();
    }
    @Test void rejectedCancellationRollsBackRelationshipAndQuota() {
        authService.follow(author.username(),voter);
        assertThatThrownBy(()->authService.unfollow(author.username(),voter)).isInstanceOf(FollowRateLimitException.class);
        assertThat(actions()).isEqualTo(1);
        assertThat(authService.getUserProfile(author.username(),voter).isFollowing()).isTrue();
        assertThat(authService.currentUser(voter).stats().following()).isEqualTo(1);
    }
    @Test void failedJournalRollsBackRelationshipGateAndNotificationWindow() {
        doThrow(new IllegalStateException("[E2E_TEST] 通知意图保存失败")).when(journal).enqueueInTransaction(any());
        assertThatThrownBy(()->authService.follow(author.username(),voter)).isInstanceOf(IllegalStateException.class);
        assertThat(actions()).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM follow_notification_windows WHERE actor_id=?",Long.class,voter.id())).isZero();
        assertThat(authService.getUserProfile(author.username(),voter).isFollowing()).isFalse();
        reset(journal);
        assertThat(authService.follow(author.username(),voter).isFollowing()).isTrue();
        assertThat(actions()).isEqualTo(1);
    }
    @Test void refollowKeepsSingleNotificationAndViewerListFlagsArePersonalized() {
        authService.follow(author.username(),voter);
        expireInterval();authService.unfollow(author.username(),voter);
        expireInterval();authService.follow(author.username(),voter);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notifications WHERE receiver_id=? AND type='FOLLOW_USER'",Long.class,author.id())).isEqualTo(1);
        assertThat(authService.listFollowing(voter.username(),1,20,voter).items().getFirst().isFollowing()).isTrue();
        assertThat(authService.listFollowing(voter.username(),1,20,null).items().getFirst().isFollowing()).isFalse();
    }
}
