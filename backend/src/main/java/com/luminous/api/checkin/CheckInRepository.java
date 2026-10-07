package com.luminous.api.checkin;

import com.luminous.api.common.BusinessException;
import com.luminous.api.common.SnowflakeIdGenerator;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

/** 数据库唯一约束是签到的幂等边界，历史记录不随 Redis 位图过期而丢失。 */
@Repository
public class CheckInRepository {
    private final JdbcTemplate jdbc;
    public CheckInRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void lockUser(Long userId) {
        if (jdbc.queryForList("SELECT id FROM users WHERE id=? FOR UPDATE", Long.class, userId).isEmpty()) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "当前用户不存在");
        }
    }

    public boolean insert(Long communityId, Long userId, LocalDate date) {
        try {
            jdbc.update("INSERT INTO community_checkins(id,community_id,user_id,checkin_date) VALUES(?,?,?,?)",
                    SnowflakeIdGenerator.getNextId(), communityId, userId, date);
            return true;
        } catch (DuplicateKeyException duplicate) { return false; }
    }

    public List<LocalDate> dates(Long communityId, Long userId, LocalDate since, LocalDate before) {
        return jdbc.query("SELECT checkin_date FROM community_checkins WHERE community_id=? AND user_id=? "
                + "AND checkin_date>=? AND checkin_date<=? ORDER BY checkin_date", (rs, row) -> rs.getDate(1).toLocalDate(),
                communityId, userId, since, before);
    }

    public int consecutiveDays(Long communityId, Long userId, LocalDate asOf) {
        int total = 0;
        LocalDate expected = asOf;
        for (int offset = 0; ; offset += 366) {
            List<LocalDate> dates = jdbc.query("SELECT checkin_date FROM community_checkins WHERE community_id=? AND user_id=? "
                    + "AND checkin_date<=? ORDER BY checkin_date DESC LIMIT 366 OFFSET ?",
                    (rs, row) -> rs.getDate(1).toLocalDate(), communityId, userId, asOf, offset);
            if (offset == 0 && !dates.isEmpty() && !dates.getFirst().equals(asOf)) { expected = asOf.minusDays(1); }
            for (LocalDate date : dates) {
                if (!date.equals(expected)) { return total; }
                total++; expected = expected.minusDays(1);
            }
            if (dates.size() < 366) { return total; }
        }
    }
}
