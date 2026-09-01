package com.luminous.api.checkin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.luminous.api.cache.CacheConstants;
import com.luminous.api.common.BusinessException;
import com.luminous.api.community.Community;
import com.luminous.api.community.CommunityMember;
import com.luminous.api.community.CommunityMemberMapper;
import com.luminous.api.community.CommunityService;
import com.luminous.api.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class CheckInService {

    private static final Logger log = LoggerFactory.getLogger(CheckInService.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final CommunityService communityService;
    private final CommunityMemberMapper communityMemberMapper;

    public CheckInService(StringRedisTemplate stringRedisTemplate,
                          CommunityService communityService,
                          CommunityMemberMapper communityMemberMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.communityService = communityService;
        this.communityMemberMapper = communityMemberMapper;
    }

    /**
     * 执行社区签到
     *
     * @param communitySlug 社区标识
     * @param currentUser   当前用户
     * @return 签到结果
     */
    @Transactional(rollbackFor = Exception.class)
    public CheckInDtos.CheckInResponse checkIn(String communitySlug, CurrentUser currentUser) {
        if (currentUser == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "未登录");
        }

        Community community = communityService.requireBySlug(communitySlug);
        
        Long count = communityMemberMapper.selectCount(new LambdaQueryWrapper<CommunityMember>()
                .eq(CommunityMember::getCommunityId, community.getId())
                .eq(CommunityMember::getUserId, currentUser.id()));
                
        if (count == null || count == 0) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "NOT_JOINED", "请先加入该社区后再签到");
        }

        LocalDate today = LocalDate.now();
        String yearMonth = today.format(DateTimeFormatter.ofPattern("yyyyMM"));
        int dayOfMonth = today.getDayOfMonth();
        
        String key = CacheConstants.CHECKIN_KEY + communitySlug + ":" + currentUser.id() + ":" + yearMonth;
        int offset = dayOfMonth - 1;
        
        try {
            Boolean alreadyCheckedIn = stringRedisTemplate.opsForValue().getBit(key, offset);
            if (Boolean.TRUE.equals(alreadyCheckedIn)) {
                return new CheckInDtos.CheckInResponse(false, 0, 0, "今日已签到");
            }
            
            stringRedisTemplate.opsForValue().setBit(key, offset, true);
            stringRedisTemplate.expire(key, 45, TimeUnit.DAYS);
            
            // 获取总签到天数和连续签到天数
            Long totalDaysLong = stringRedisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Long>) connection -> 
                    connection.stringCommands().bitCount(key.getBytes()));
            int totalDays = totalDaysLong != null ? totalDaysLong.intValue() : 1;
            
            int consecutiveDays = calculateConsecutiveDays(key, dayOfMonth);
            
            return new CheckInDtos.CheckInResponse(true, totalDays, consecutiveDays, "签到成功");
        } catch (Exception e) {
            log.error("Redis error during checkIn for user {} in community {}", currentUser.id(), communitySlug, e);
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "CHECKIN_FAILED", "签到失败，请稍后重试");
        }
    }

    /**
     * 获取签到统计信息
     *
     * @param communitySlug 社区标识
     * @param currentUser   当前用户
     * @return 签到统计
     */
    public CheckInDtos.CheckInStats getStats(String communitySlug, CurrentUser currentUser) {
        if (currentUser == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "未登录");
        }

        Community community = communityService.requireBySlug(communitySlug);

        LocalDate today = LocalDate.now();
        String yearMonth = today.format(DateTimeFormatter.ofPattern("yyyyMM"));
        int dayOfMonth = today.getDayOfMonth();
        
        String key = CacheConstants.CHECKIN_KEY + communitySlug + ":" + currentUser.id() + ":" + yearMonth;
        
        try {
            Long totalDaysLong = stringRedisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Long>) connection -> 
                    connection.stringCommands().bitCount(key.getBytes()));
            int totalDays = totalDaysLong != null ? totalDaysLong.intValue() : 0;
            
            Boolean checkedToday = stringRedisTemplate.opsForValue().getBit(key, dayOfMonth - 1);
            if (checkedToday == null) {
                checkedToday = false;
            }
            
            int consecutiveDays = calculateConsecutiveDays(key, dayOfMonth);
            List<Integer> checkedDays = getCheckedDays(key, dayOfMonth);
            
            return new CheckInDtos.CheckInStats(totalDays, consecutiveDays, checkedToday, checkedDays);
        } catch (Exception e) {
            log.error("Redis error getting checkIn stats for user {} in community {}", currentUser.id(), communitySlug, e);
            // 降级处理
            return new CheckInDtos.CheckInStats(0, 0, false, new ArrayList<>());
        }
    }

    /**
     * 计算连续签到天数
     */
    private int calculateConsecutiveDays(String key, int todayDayOfMonth) {
        try {
            List<Long> results = stringRedisTemplate.opsForValue()
                .bitField(key, BitFieldSubCommands.create()
                    .get(BitFieldSubCommands.BitFieldType.unsigned(todayDayOfMonth)).valueAt(0));
                    
            if (results == null || results.isEmpty() || results.get(0) == null) {
                return 0;
            }
            
            long bits = results.get(0);
            
            // 若今日尚未签到，则检查昨日是否签到（若昨日签到则以昨日为基准展示连续天数，若昨日也未签到则断签重置为0）
            if ((bits & 1) == 0) {
                bits >>= 1;
            }
            
            int count = 0;
            while ((bits & 1) == 1) {
                count++;
                bits >>= 1;
            }
            return count;
        } catch (Exception e) {
            log.error("Error calculating consecutive check-in days for key {}", key, e);
            return 0;
        }
    }

    /**
     * 获取当月已签到的日期列表
     */
    private List<Integer> getCheckedDays(String key, int todayDayOfMonth) {
        List<Integer> checkedDays = new ArrayList<>();
        try {
            for (int i = 1; i <= todayDayOfMonth; i++) {
                Boolean isSet = stringRedisTemplate.opsForValue().getBit(key, i - 1);
                if (Boolean.TRUE.equals(isSet)) {
                    checkedDays.add(i);
                }
            }
        } catch (Exception e) {
            log.error("Error getting checked days for key {}", key, e);
        }
        return checkedDays;
    }
}
