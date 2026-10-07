package com.luminous.api.job;

import com.luminous.api.vote.VoteRepository;
import com.luminous.api.vote.VoteReconciliationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 修复投票物化记录和缓存，积压事件通过可靠投递任务继续处理。 */
@Component
public class DataReconciliationScheduler {
    private static final Logger log = LoggerFactory.getLogger(DataReconciliationScheduler.class);
    private final VoteRepository votes;
    private final VoteReconciliationService reconciliation;
    public DataReconciliationScheduler(VoteRepository votes, VoteReconciliationService reconciliation) { this.votes = votes; this.reconciliation = reconciliation; }

    @Scheduled(cron = "${app.reconciliation.cron:0 0/15 * * * ?}")
    public void reconcileActivePosts() {
        for (Long postId : votes.activePosts()) { reconcilePostVotes(postId); }
    }

    public boolean reconcilePostVotes(Long postId) {
        if (postId == null) { return false; }
        try { return reconciliation.reconcile(postId); }
        catch (Exception ex) { log.error("投票对账未完成，postId={}", postId, ex); return false; }
    }
}
