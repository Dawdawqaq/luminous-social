package com.luminous.api.checkin;

import java.util.List;

public final class CheckInDtos {
    private CheckInDtos() {}

    /** 当月签到总数与跨月连续天数，重复签到仍返回真实统计。 */
    public record CheckInResponse(boolean success, int totalDays, int consecutiveDays, String message) {}

    /** 所选月份的签到总数和日期；连续天数计算到当日或历史月份最后一天。 */
    public record CheckInStats(int totalDays, int consecutiveDays, boolean checkedToday, List<Integer> checkedDays) {}
}
