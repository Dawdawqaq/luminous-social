-- 操作者级串行化只约束自己的关系变更，不锁定被关注用户的全部粉丝。
CREATE TABLE follow_action_gates (
    user_id BIGINT PRIMARY KEY,
    last_changed_at DATETIME(6) NULL,
    CONSTRAINT fk_follow_gate_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE follow_actions (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    target_id BIGINT NOT NULL,
    following BOOLEAN NOT NULL,
    changed_at DATETIME(6) NOT NULL,
    INDEX idx_follow_action_window(user_id,changed_at),
    INDEX idx_follow_action_expiry(changed_at),
    CONSTRAINT fk_follow_action_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 保存通知意图的时间与关系写入共用事务，发送失败由可靠事件补发。
CREATE TABLE follow_notification_windows (
    actor_id BIGINT NOT NULL,
    receiver_id BIGINT NOT NULL,
    last_event_at DATETIME(6) NULL,
    PRIMARY KEY(actor_id,receiver_id),
    INDEX idx_follow_notification_expiry(last_event_at),
    CONSTRAINT fk_follow_notification_actor FOREIGN KEY(actor_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
