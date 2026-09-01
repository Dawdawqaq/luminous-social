package com.luminous.api.checkin;

import java.util.List;

public final class CheckInDtos {
    private CheckInDtos() {}
    
    /**
     * Response after checking in
     */
    public record CheckInResponse(
        boolean success,
        int totalDays,        // total check-in days this month
        int consecutiveDays,  // current consecutive days
        String message
    ) {}
    
    /**
     * Check-in statistics for a community
     */
    public record CheckInStats(
        int totalDays,        // total check-in days this month
        int consecutiveDays,  // current consecutive days streak
        boolean checkedToday, // whether already checked in today
        List<Integer> checkedDays // list of days checked in this month (1-31)
    ) {}
}
