package com.luminous.api.growth;

import com.luminous.api.common.BusinessException;
import com.luminous.api.common.PageResponse;
import com.luminous.api.common.PaginationPolicy;
import com.luminous.api.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Service
public class GrowthQueryService {
    private final GrowthRepository repository;
    private final GrowthProperties properties;
    private final LevelCalculator calculator;
    private final JdbcTemplate jdbc;
    public GrowthQueryService(GrowthRepository repository, GrowthProperties properties, LevelCalculator calculator, JdbcTemplate jdbc) {
        this.repository = repository; this.properties = properties; this.calculator = calculator; this.jdbc = jdbc;
    }

    public GrowthDtos.Progress progress(long userId, long communityId) {
        if (!properties.isEnabled()) return null;
        Long balance = repository.balance(userId, communityId);
        return balance == null ? null : calculator.calculate(balance);
    }

    public Map<Long, GrowthDtos.Progress> progresses(Collection<Long> userIds, long communityId) {
        if (!properties.isEnabled()) return Map.of();
        Map<Long, GrowthDtos.Progress> result = new HashMap<>();
        repository.balances(userIds, communityId).forEach((id, balance) -> result.put(id, calculator.calculate(balance)));
        return result;
    }

    @Transactional(readOnly = true)
    public GrowthDtos.Overview overview(String username, String community) {
        var users = jdbc.queryForList("SELECT id FROM users WHERE username=?", Long.class, username);
        if (users.isEmpty()) throw new BusinessException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在");
        long userId = users.getFirst();
        Long communityId = null;
        if (community != null && !community.isBlank()) {
            var ids = jdbc.queryForList("SELECT id FROM communities WHERE slug=?", Long.class, community);
            if (ids.isEmpty()) throw new BusinessException(HttpStatus.NOT_FOUND, "COMMUNITY_NOT_FOUND", "社区不存在");
            communityId = ids.getFirst();
        }
        return new GrowthDtos.Overview(progress(userId, 0), communityId == null ? null : progress(userId, communityId),
                community, properties.isEnabled(), properties.getRuleVersion(), properties.isProvisional());
    }

    public Map<GrowthRepository.AccountKey, GrowthDtos.Progress> authorProgresses(Collection<Long> userIds, Collection<Long> communityIds) {
        if (!properties.isEnabled()) return Map.of();
        var scopes = new java.util.HashSet<>(communityIds);
        scopes.add(0L);
        Map<GrowthRepository.AccountKey, GrowthDtos.Progress> result = new HashMap<>();
        repository.balances(userIds, scopes).forEach((key, balance) -> result.put(key, calculator.calculate(balance)));
        return result;
    }

    @Transactional(readOnly = true)
    public PageResponse<GrowthDtos.LedgerEntry> ledger(CurrentUser user, long page, long size) {
        if (user == null) throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录");
        PaginationPolicy.validate(page, size);
        long count = repository.ledgerCount(user.id());
        return new PageResponse<>(repository.ledger(user.id(), page, size), page, size, count, page < (count + size - 1) / size);
    }
}
