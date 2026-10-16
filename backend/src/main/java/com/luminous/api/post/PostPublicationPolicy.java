package com.luminous.api.post;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.time.LocalDateTime;

/** 与帖子共用事务和用户行锁，仅成功发布占用额度，跨实例保持一致。 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class PostPublicationPolicy {
    private final PostPublicationRepository repository;
    public PostPublicationPolicy(PostPublicationRepository repository) { this.repository = repository; }

    public void check(long userId) {
        LocalDateTime last = repository.lockUser(userId);
        LocalDateTime now = repository.now();
        var times = repository.recent(userId, now.minusMinutes(10));
        long nanos = last == null ? 0 : Duration.between(now, last.plusSeconds(30)).toNanos();
        if (times.size() >= 10) nanos = Math.max(nanos, Duration.between(now, times.getFirst().plusMinutes(10)).toNanos());
        if (nanos > 0) throw new PostRateLimitException((int) Math.max(1, (nanos + 999_999_999) / 1_000_000_000));
    }

    public void record(long userId, long postId) { repository.record(userId, postId); }
}
