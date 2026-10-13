package com.luminous.api.growth;

import com.luminous.api.common.BusinessException;
import com.luminous.api.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/** 受控计时阅读只是可配置的候选资格标准，不把普通访问或前端自行上报的曝光量当作经验。 */
@Service
public class GrowthReadService {
    private final GrowthRepository repository;
    private final GrowthService growth;
    private final GrowthProperties properties;
    private final Clock clock;
    public GrowthReadService(GrowthRepository repository, GrowthService growth, GrowthProperties properties, Clock clock) {
        this.repository = repository; this.growth = growth; this.properties = properties; this.clock = clock;
    }

    @Transactional
    public GrowthDtos.ReadSession begin(long postId, CurrentUser user) {
        requireEnabled(user);
        var content = repository.lockPost(postId);
        requireEligible(content, user);
        var now = clock.instant();
        var session = new GrowthRepository.Session(UUID.randomUUID().toString(), postId, user.id(), now,
                now.plusSeconds(properties.getReadSessionSeconds()), false);
        repository.insertSession(session);
        return new GrowthDtos.ReadSession(session.token(), now.plusSeconds(properties.getReadMinimumSeconds()),
                session.expiresAt(), properties.getRuleVersion(), properties.isProvisional());
    }

    @Transactional
    public GrowthDtos.ReadResult complete(long postId, String token, CurrentUser user) {
        requireEnabled(user);
        var peek = repository.session(token, false);
        if (peek == null || peek.postId() != postId || peek.readerId() != user.id()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "READ_SESSION_NOT_FOUND", "阅读会话不存在");
        }
        var content = repository.lockPost(postId);
        requireEligible(content, user);
        var session = repository.session(token, true);
        if (session == null) throw new BusinessException(HttpStatus.GONE, "READ_SESSION_EXPIRED", "阅读会话已过期");
        if (session.completed()) return new GrowthDtos.ReadResult(false, 0);
        var now = clock.instant();
        if (!now.isBefore(session.expiresAt())) throw new BusinessException(HttpStatus.GONE, "READ_SESSION_EXPIRED", "阅读会话已过期");
        if (now.isBefore(session.startedAt().plusSeconds(properties.getReadMinimumSeconds()))) {
            throw new BusinessException(HttpStatus.CONFLICT, "READ_DURATION_INSUFFICIENT", "阅读时长尚未达到当前规则要求");
        }
        boolean first = repository.qualify(postId, user.id(), now);
        long amount = first ? growth.readReward(content, repository.incrementReads(postId)) : 0;
        repository.completeSession(token);
        return new GrowthDtos.ReadResult(first, amount);
    }

    private void requireEligible(GrowthRepository.Content content, CurrentUser user) {
        if (content == null || !content.eligible()) throw new BusinessException(HttpStatus.NOT_FOUND, "POST_NOT_FOUND", "帖子不存在");
        // 阅读完成后可能向作者发奖，先按统一顺序锁定作者与读者，避免账户之后补锁用户。
        repository.lockUsers(content.userId(), user.id());
        if (content.userId() == user.id() || !repository.activeUser(user.id()) || !repository.prepareSubject(content)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "READ_NOT_ELIGIBLE", "本次阅读不满足奖励资格");
        }
    }

    private void requireEnabled(CurrentUser user) {
        if (user == null) throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录");
        if (!properties.isEnabled() || !properties.isReadEnabled()) {
            throw new BusinessException(HttpStatus.CONFLICT, "READ_REWARD_DISABLED", "有效阅读奖励标准尚未启用");
        }
    }

    @Scheduled(fixedDelay = 3600000)
    public void cleanup() {
        if (properties.isEnabled() && properties.isReadEnabled()) repository.removeExpiredSessions(clock.instant());
    }
}
