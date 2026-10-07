package com.luminous.api.checkin;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;
import java.time.ZoneId;

/** 签到日界固定采用上海时区，避免多实例操作系统时区不同导致重复签到。 */
@Configuration
public class CheckInClockConfiguration {
    @Bean public Clock checkInClock() { return Clock.system(ZoneId.of("Asia/Shanghai")); }
}
