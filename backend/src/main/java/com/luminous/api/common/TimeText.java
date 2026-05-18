package com.luminous.api.common;

import java.time.Duration;
import java.time.LocalDateTime;

public final class TimeText {
    private TimeText() {
    }

    public static String from(LocalDateTime time) {
        Duration duration = Duration.between(time, LocalDateTime.now());
        if (duration.toMinutes() < 1) {
            return "刚刚";
        }
        if (duration.toHours() < 1) {
            return duration.toMinutes() + "分钟前";
        }
        if (duration.toDays() < 1) {
            return duration.toHours() + "小时前";
        }
        return duration.toDays() + "天前";
    }
}
