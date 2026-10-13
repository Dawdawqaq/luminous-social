package com.luminous.api.growth;

import com.luminous.api.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HexFormat;

import static com.luminous.api.growth.GrowthProperties.Action;

/** 奖励、额度和双维度余额原子更新；零奖励决策也保留，防止跨日重试补领。 */
@Service
public class GrowthService {
    private final GrowthRepository repository;
    private final GrowthProperties properties;
    private final Clock clock;
    private final JdbcTemplate jdbc;
    public GrowthService(GrowthRepository repository, GrowthProperties properties, Clock clock, JdbcTemplate jdbc) {
        this.repository = repository; this.properties = properties; this.clock = clock; this.jdbc = jdbc;
    }

    @Transactional
    public void initialize(long userId, long communityId) {
        if (!properties.isEnabled()) return;
        repository.initialize(userId, 0);
        if (communityId != 0) repository.initialize(userId, communityId);
    }

    @Transactional
    public long checkIn(long userId, long communityId, LocalDate date) {
        if (!properties.isEnabled()) return 0;
        // 奖励只接受已持久化签到，不信任外部提供的日期或社区身份。
        if (!date.equals(LocalDate.now(clock)) || jdbc.queryForObject(
                "SELECT COUNT(*) FROM community_checkins WHERE user_id=? AND community_id=? AND checkin_date=?",
                Long.class, userId, communityId, date) == 0) return 0;
        var content = new GrowthRepository.Content("COMMUNITY", communityId, userId, communityId, 0, true, "");
        return grant("CHECK_IN:" + userId + ":" + communityId + ":" + date, content, null, Action.CHECK_IN, true, "社区签到", date);
    }

    @Transactional
    public long created(String type, long contentId) {
        if (!properties.isEnabled()) return 0;
        var content = repository.lockContent(type, contentId);
        if (content == null || !content.eligible() || !repository.prepareSubject(content)) return 0;
        repository.lockAccounts(content.userId(), content.communityId());
        String source = type + ":" + contentId;
        Action action = "POST".equals(type) ? Action.POST : Action.COMMENT;
        LocalDate date = LocalDate.now(clock);
        var pending = repository.initializeReward(source, content, null, action.name(), date, properties.getRuleVersion());
        if (!"PENDING".equals(pending.state())) return 0;
        String text = normalize(content.text());
        int minimum = action == Action.POST ? properties.getMinimumPostCharacters() : properties.getMinimumCommentCharacters();
        boolean eligible = text.codePointCount(0, text.length()) >= minimum;
        if (action == Action.COMMENT) {
            var parent = repository.lockPost(content.postId());
            eligible &= parent != null && parent.userId() != content.userId()
                    && repository.commentsAwarded(content.userId(), content.postId(), date)
                    < properties.getRewardedCommentsPerPostPerDay();
        }
        eligible &= repository.fingerprint(content.userId(), action.name(), hash(text), contentId);
        return completeGrant(pending, content, action, eligible, eligible ? "有效发布" : "未满足开发规则中的有效发布条件", date);
    }

    @Transactional
    public void liked(String type, long contentId, long actorId) {
        var content = repository.lockContent(type, contentId);
        if (content == null || content.userId() == actorId) return;
        String source = "LIKE:" + type + ":" + contentId + ":" + actorId;
        // 奖励关闭且没有历史来源时不创建成长账户；已有奖励仍必须响应取消。
        if (!properties.isEnabled() && !repository.rewardExists(source)) return;
        // 双方资格锁先于成长账户，按编号顺序获取，取消奖励也沿用相同顺序。
        repository.lockUsers(content.userId(), actorId);
        repository.lockAccounts(content.userId(), content.communityId());
        LocalDate date = LocalDate.now(clock);
        var existing = repository.initializeReward(source, content, actorId, Action.LIKE.name(), date, properties.getRuleVersion());
        // 读取数据库最新投票意图，不让重复或旧事件恢复已经取消的点赞。
        boolean positive = repository.currentLike(content, actorId) == 1;
        if (!positive) {
            if ("ACTIVE".equals(existing.state())) {
                repository.changeBalances(existing.userId(), existing.communityId(), -existing.amount());
                repository.transition(existing, "CANCELLED", -existing.amount(), "CANCEL", "有效点赞已取消或转为踩");
            }
            repository.discardPendingReward(existing);
            return;
        }
        if (!properties.isEnabled() || !content.eligible() || !repository.prepareSubject(content)
                || !repository.activeUser(actorId) || !repository.activeUser(content.userId())) {
            repository.discardPendingReward(existing);
            return;
        }
        if (!"PENDING".equals(existing.state())) {
            if ("CANCELLED".equals(existing.state())) {
                repository.changeBalances(existing.userId(), existing.communityId(), existing.amount());
                repository.transition(existing, "ACTIVE", existing.amount(), "RESTORE", "恢复原点赞奖励，不重复占用额度");
            }
            return;
        }
        completeGrant(existing, content, Action.LIKE, true, "独立账号有效点赞", date);
    }

    @Transactional
    public long readReward(GrowthRepository.Content content, long qualifiedReaders) {
        if (!properties.isEnabled() || !properties.isReadEnabled() || !content.eligible()) return 0;
        if (qualifiedReaders % properties.getReadersPerReward() != 0) return 0;
        return grant("READ:" + content.id() + ":" + qualifiedReaders / properties.getReadersPerReward(),
                content, null, Action.READ, true, "受控阅读资格累计达到配置门槛", LocalDate.now(clock));
    }

    @Transactional
    public void removed(String type, long contentId, boolean moderated, String reason) {
        var content = moderated ? lockModerationContent(type, contentId) : repository.lockContent(type, contentId);
        if (content == null) return;
        repository.block(content, reason);
        // 正常删除只终止后续奖励；违规处理才撤销已经实际发放的经验。
        if (moderated) revoke(content, reason);
    }

    @Transactional
    public void banPost(long postId, String reason) {
        var content = lockModerationContent("POST", postId);
        if (content == null) throw new BusinessException(HttpStatus.NOT_FOUND, "POST_NOT_FOUND", "帖子不存在");
        repository.block(content, reason);
        repository.banPost(postId);
        revoke(content, reason);
    }

    @Transactional
    public void revokeComment(long commentId, String reason) {
        var content = lockModerationContent("COMMENT", commentId);
        if (content == null) throw new BusinessException(HttpStatus.NOT_FOUND, "COMMENT_NOT_FOUND", "评论及成长来源不存在");
        repository.block(content, reason);
        revoke(content, reason);
    }

    private GrowthRepository.Content lockModerationContent(String type, long contentId) {
        // 先按原顺序锁定存活内容；删除事务提交后，再锁留存主体串行处理追责。
        var content = repository.lockContent(type, contentId);
        return content == null ? repository.lockRetainedContent(type, contentId) : content;
    }

    private void revoke(GrowthRepository.Content content, String reason) {
        var rewards = repository.contentRewards(content);
        if (rewards.isEmpty()) return;
        repository.lockAccounts(content.userId(), content.communityId());
        for (var reward : rewards) {
            if ("REVOKED".equals(reward.state())) continue;
            long delta = "ACTIVE".equals(reward.state()) ? -reward.amount() : 0;
            if (delta != 0) repository.changeBalances(reward.userId(), reward.communityId(), delta);
            repository.transition(reward, "REVOKED", delta, "REVOKE", reason);
        }
    }

    private long grant(String source, GrowthRepository.Content content, Long actorId, Action action, boolean eligible, String reason, LocalDate date) {
        repository.lockAccounts(content.userId(), content.communityId());
        var pending = repository.initializeReward(source, content, actorId, action.name(), date, properties.getRuleVersion());
        if (!"PENDING".equals(pending.state())) return 0;
        return completeGrant(pending, content, action, eligible, reason, date);
    }

    private long completeGrant(GrowthRepository.Reward pending, GrowthRepository.Content content, Action action,
                               boolean eligible, String reason, LocalDate date) {
        eligible &= repository.activeUser(content.userId());
        var rule = properties.getRewards().get(action);
        // 同一笔奖励只读取一次业务日期，避免午夜附近额度日期与流水日期不一致。
        long amount = eligible ? repository.reserve(content.userId(), date, action.name(), rule.amount(), rule.dailyCap()) : 0;
        String decision = !eligible ? reason : amount == 0 ? "已达到当日全站经验上限或该行为奖励为零" : reason;
        repository.completeReward(pending, amount, decision);
        if (amount != 0) repository.changeBalances(content.userId(), content.communityId(), amount);
        return amount;
    }

    private static String normalize(String text) {
        return Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFKC).replaceAll("\\s+", "").toLowerCase(java.util.Locale.ROOT);
    }

    private static String hash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException error) { throw new IllegalStateException("运行环境缺少 SHA-256", error); }
    }
}
