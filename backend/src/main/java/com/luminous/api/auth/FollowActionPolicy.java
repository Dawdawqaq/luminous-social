package com.luminous.api.auth;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/** 实际关系变更共用一秒间隔及一分钟三十次额度，重复状态不计费。 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class FollowActionPolicy {
    private final FollowActionRepository repository;
    public FollowActionPolicy(FollowActionRepository repository) { this.repository = repository; }
    public boolean change(long actorId,long targetId,boolean following) {
        FollowActionRepository.Gate gate=repository.lockActor(actorId);
        // 先按受影响行数确定是否变更；限频失败会回滚刚写入的关系，因此重复状态无需占额度。
        if(repository.changeRelation(actorId,targetId,following)==0) return false;
        LocalDateTime now=repository.now();
        List<LocalDateTime> recent=gate.recentChanges().stream().filter(time->time.isAfter(now.minusSeconds(60))).sorted().toList();
        check(gate.lastChangedAt(),now,recent);
        repository.record(actorId,targetId,following,now,recent);
        return true;
    }

    private void check(LocalDateTime last,LocalDateTime now,List<LocalDateTime> recent) {
        long wait = last == null ? 0 : Duration.between(now,last.plusSeconds(1)).toNanos();
        if(recent.size() >= 30) wait = Math.max(wait,Duration.between(now,recent.getFirst().plusSeconds(60)).toNanos());
        if(wait > 0) throw new FollowRateLimitException((int)Math.max(1,(wait+999_999_999)/1_000_000_000));
    }
    public boolean shouldNotify(long actorId,long receiverId) { return repository.claimNotification(actorId,receiverId); }
}
