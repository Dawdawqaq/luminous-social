-- 有界时间窗口保存在操作者主键行内，避免在空操作历史上加间隙锁。
ALTER TABLE follow_action_gates
    ADD COLUMN recent_change_times VARCHAR(2048) NOT NULL DEFAULT '';

-- 统一字段命名，兼容 H2 的保留关键字并保留已应用的 V21 原文。
ALTER TABLE follow_actions RENAME COLUMN `following` TO is_following;

-- 保留已有窗口记录，升级时不重置最近一分钟的额度。
UPDATE follow_action_gates g
JOIN (
    SELECT user_id,GROUP_CONCAT(DATE_FORMAT(changed_at,'%Y-%m-%dT%H:%i:%s.%f') ORDER BY changed_at SEPARATOR ',') AS times
    FROM follow_actions
    WHERE changed_at > CURRENT_TIMESTAMP(6)-INTERVAL 60 SECOND
    GROUP BY user_id
) a ON a.user_id=g.user_id
SET g.recent_change_times=a.times;
