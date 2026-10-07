package com.luminous.api.checkin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.luminous.api.cache.CacheConstants;
import com.luminous.api.common.AfterCommit;
import com.luminous.api.common.BusinessException;
import com.luminous.api.community.*;
import com.luminous.api.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** 数据库原子签到，Redis 位图仅作提交后的短期投影；连续天数跨月计算。 */
@Service
public class CheckInService {
    private static final Logger log = LoggerFactory.getLogger(CheckInService.class);
    private final StringRedisTemplate redis;
    private final CommunityService communities;
    private final CommunityMemberMapper members;
    private final CheckInRepository repository;
    private final Clock clock;
    public CheckInService(StringRedisTemplate redis, CommunityService communities, CommunityMemberMapper members,
                          CheckInRepository repository, Clock clock) {
        this.redis = redis; this.communities = communities; this.members = members; this.repository = repository; this.clock = clock;
    }

    @Transactional(rollbackFor = Exception.class)
    public CheckInDtos.CheckInResponse checkIn(String slug, CurrentUser user) {
        requireLogin(user);
        // 在任何一致性读取前获取用户行锁，重复请求能读到前一次提交的签到与统计。
        repository.lockUser(user.id());
        Community community = communities.requireBySlug(slug);
        Long count = members.selectCount(new LambdaQueryWrapper<CommunityMember>()
                .eq(CommunityMember::getCommunityId, community.getId()).eq(CommunityMember::getUserId, user.id()));
        if (count == null || count == 0) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "NOT_JOINED", "请先加入该社区后再签到");
        }
        LocalDate today = LocalDate.now(clock);
        boolean first = repository.insert(community.getId(), user.id(), today);
        CheckInDtos.CheckInStats stats = stats(community.getId(), user.id(), YearMonth.from(today), today);
        AfterCommit.run(() -> project(slug, user.id(), today));
        return new CheckInDtos.CheckInResponse(first, stats.totalDays(), stats.consecutiveDays(), first ? "签到成功" : "今日已签到");
    }

    @Transactional(readOnly = true)
    public CheckInDtos.CheckInStats getStats(String slug, CurrentUser user) { return getStats(slug, user, null); }

    @Transactional(readOnly = true)
    public CheckInDtos.CheckInStats getStats(String slug, CurrentUser user, String month) {
        requireLogin(user);
        Community community = communities.requireBySlug(slug);
        LocalDate today = LocalDate.now(clock);
        YearMonth selected;
        try {
            selected = month == null ? YearMonth.from(today) : YearMonth.parse(month);
            if (selected.getYear() < 1970 || selected.isAfter(YearMonth.from(today))) { throw new IllegalArgumentException(); }
        } catch (Exception ex) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_CHECKIN_MONTH", "月份必须为 yyyy-MM，且不能晚于当前月份");
        }
        return stats(community.getId(), user.id(), selected, today);
    }

    private CheckInDtos.CheckInStats stats(Long communityId, Long userId, YearMonth selected, LocalDate today) {
        LocalDate asOf = selected.equals(YearMonth.from(today)) ? today : selected.atEndOfMonth();
        List<LocalDate> dates = repository.dates(communityId, userId, selected.atDay(1), asOf);
        boolean checkedToday = selected.equals(YearMonth.from(today)) && dates.contains(today);
        return new CheckInDtos.CheckInStats(dates.size(), repository.consecutiveDays(communityId, userId, asOf),
                checkedToday, dates.stream().map(LocalDate::getDayOfMonth).toList());
    }

    private void project(String slug, Long userId, LocalDate today) {
        String key = CacheConstants.CHECKIN_KEY + slug + ":" + userId + ":" + today.format(DateTimeFormatter.ofPattern("yyyyMM"));
        try {
            // SETBIT 原子返回旧值，但业务首次成功由数据库唯一约束决定。
            redis.opsForValue().setBit(key, today.getDayOfMonth() - 1, true);
            redis.expire(key, 45, TimeUnit.DAYS);
        } catch (Exception ex) { log.warn("签到已提交，Redis 位图投影失败不影响历史与统计", ex); }
    }

    private static void requireLogin(CurrentUser user) {
        if (user == null) { throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "未登录"); }
    }
}
