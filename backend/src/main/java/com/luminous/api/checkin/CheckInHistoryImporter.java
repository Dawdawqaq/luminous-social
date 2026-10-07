package com.luminous.api.checkin;

import com.luminous.api.cache.CacheConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** 升级时显式启用，导入仍存活的旧位图；每个用户月份独立事务，重复执行安全。 */
@Component
public class CheckInHistoryImporter implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(CheckInHistoryImporter.class);
    private final StringRedisTemplate redis;
    private final JdbcTemplate jdbc;
    private final CheckInRepository repository;
    private final TransactionTemplate transaction;
    private final Clock clock;
    @Value("${app.checkin.import-legacy-history:false}") private boolean enabled;
    public CheckInHistoryImporter(StringRedisTemplate redis, JdbcTemplate jdbc, CheckInRepository repository,
                                  PlatformTransactionManager manager, Clock clock) {
        this.redis = redis; this.jdbc = jdbc; this.repository = repository;
        this.transaction = new TransactionTemplate(manager); this.clock = clock;
    }

    @Override public void run(ApplicationArguments arguments) {
        if (!enabled) { return; }
        int inserted = 0;
        // 只扫描本应用签到前缀，不使用 KEYS 或访问其他项目的数据。
        try (Cursor<String> keys = redis.scan(ScanOptions.scanOptions().match(CacheConstants.CHECKIN_KEY + "*").count(100).build())) {
            while (keys.hasNext()) { inserted += importMonth(keys.next()); }
        }
        log.info("签到旧位图导入完成，新增历史记录 {} 条；后续启动可关闭导入开关", inserted);
    }

    public int importMonth(String key) {
        if (key == null || !key.startsWith(CacheConstants.CHECKIN_KEY)) { throw new IllegalArgumentException("只允许导入本应用签到键"); }
        String suffix = key.substring(CacheConstants.CHECKIN_KEY.length());
        int monthSeparator = suffix.lastIndexOf(':');
        int userSeparator = monthSeparator < 0 ? -1 : suffix.lastIndexOf(':', monthSeparator - 1);
        if (userSeparator < 1 || monthSeparator < 0) { return 0; }
        String slug = suffix.substring(0, userSeparator);
        Long userId; YearMonth month;
        try {
            userId = Long.valueOf(suffix.substring(userSeparator + 1, monthSeparator));
            String rawMonth = suffix.substring(monthSeparator + 1);
            if (!rawMonth.matches("[0-9]{6}")) { return 0; }
            month = YearMonth.parse(rawMonth, DateTimeFormatter.ofPattern("yyyyMM"));
            if (month.getYear() < 1970 || month.isAfter(YearMonth.from(LocalDate.now(clock)))) { return 0; }
        } catch (RuntimeException ex) { log.warn("忽略格式无效的历史签到键：{}", key); return 0; }
        byte[] bitmap = redis.execute((RedisCallback<byte[]>) connection -> connection.stringCommands().get(key.getBytes(StandardCharsets.UTF_8)));
        if (bitmap == null || bitmap.length == 0) { return 0; }
        List<Long> communities = jdbc.queryForList("SELECT id FROM communities WHERE slug=?", Long.class, slug);
        if (communities.isEmpty() || jdbc.queryForList("SELECT id FROM users WHERE id=?", Long.class, userId).isEmpty()) { return 0; }
        Long communityId = communities.getFirst();
        LocalDate today = LocalDate.now(clock);
        Integer result = transaction.execute(status -> {
            repository.lockUser(userId);
            int inserted = 0;
            for (int day = 1; day <= month.lengthOfMonth(); day++) {
                int bit = day - 1;
                LocalDate date = month.atDay(day);
                if (!date.isAfter(today) && bit / 8 < bitmap.length && (bitmap[bit / 8] & (1 << (7 - bit % 8))) != 0
                        && repository.insert(communityId, userId, date)) { inserted++; }
            }
            return inserted;
        });
        return result == null ? 0 : result;
    }
}
