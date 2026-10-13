package com.luminous.api.post;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/** 主评论与楼内回复共用用户额度；五秒最小间隔与一分钟十条同时生效。 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class CommentPublicationPolicy {
    private final CommentPublicationRepository repository;
    public CommentPublicationPolicy(CommentPublicationRepository repository) { this.repository = repository; }

    public void check(long userId) {
        LocalDateTime last = repository.lockUser(userId);
        LocalDateTime now = repository.now();
        List<LocalDateTime> recent = repository.recent(userId, now.minusSeconds(60));
        long waitNanos = last == null ? 0 : Duration.between(now, last.plusSeconds(5)).toNanos();
        if (recent.size() >= 10) waitNanos = Math.max(waitNanos, Duration.between(now, recent.getFirst().plusSeconds(60)).toNanos());
        if (waitNanos > 0) throw new CommentRateLimitException((int) Math.max(1, (waitNanos + 999_999_999) / 1_000_000_000));
    }

    public void record(long userId, long commentId) { repository.record(userId, commentId); }
}
