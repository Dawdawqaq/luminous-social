-- 成长余额、奖励决策和变动流水同事务提交，内容删除后仍保留来源编号。
CREATE TABLE growth_accounts (
    user_id BIGINT NOT NULL,
    community_id BIGINT NOT NULL DEFAULT 0,
    experience BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, community_id),
    CONSTRAINT fk_growth_account_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT ck_growth_account_experience CHECK (experience >= 0)
) ENGINE=InnoDB;

CREATE TABLE growth_daily_usage (
    user_id BIGINT NOT NULL,
    reward_date DATE NOT NULL,
    action VARCHAR(24) NOT NULL,
    used_experience BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, reward_date, action),
    CONSTRAINT fk_growth_usage_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE growth_subjects (
    content_type VARCHAR(16) NOT NULL,
    content_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    community_id BIGINT NOT NULL,
    blocked BOOLEAN NOT NULL DEFAULT FALSE,
    reason VARCHAR(255),
    qualified_reads BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (content_type, content_id),
    INDEX idx_growth_subject_user (user_id),
    CONSTRAINT fk_growth_subject_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE growth_rewards (
    id BIGINT PRIMARY KEY,
    source_key VARCHAR(191) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id BIGINT NOT NULL,
    community_id BIGINT NOT NULL,
    action VARCHAR(24) NOT NULL,
    content_type VARCHAR(16) NOT NULL,
    content_id BIGINT NOT NULL,
    actor_id BIGINT,
    parent_post_id BIGINT NOT NULL DEFAULT 0,
    reward_date DATE NOT NULL,
    amount BIGINT NOT NULL,
    state VARCHAR(16) NOT NULL,
    revision BIGINT NOT NULL DEFAULT 1,
    reason VARCHAR(255) NOT NULL,
    rule_version VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_growth_reward_source (source_key),
    INDEX idx_growth_reward_content (content_type, content_id, state),
    INDEX idx_growth_reward_user (user_id, id),
    INDEX idx_growth_reward_comment_limit (user_id, action, reward_date, parent_post_id),
    CONSTRAINT fk_growth_reward_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT ck_growth_reward_amount CHECK (amount >= 0)
) ENGINE=InnoDB;

CREATE TABLE growth_ledger (
    id BIGINT PRIMARY KEY,
    reward_id BIGINT NOT NULL,
    revision BIGINT NOT NULL,
    delta BIGINT NOT NULL,
    operation VARCHAR(16) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_growth_ledger_revision (reward_id, revision),
    CONSTRAINT fk_growth_ledger_reward FOREIGN KEY (reward_id) REFERENCES growth_rewards(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE growth_content_fingerprints (
    user_id BIGINT NOT NULL,
    action VARCHAR(24) NOT NULL,
    fingerprint CHAR(64) CHARACTER SET ascii NOT NULL,
    content_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, action, fingerprint),
    CONSTRAINT fk_growth_fingerprint_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE growth_read_sessions (
    token CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    post_id BIGINT NOT NULL,
    reader_id BIGINT NOT NULL,
    started_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    INDEX idx_growth_session_expiry (expires_at),
    INDEX idx_growth_session_reader (reader_id),
    CONSTRAINT fk_growth_session_reader FOREIGN KEY (reader_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE growth_qualified_reads (
    post_id BIGINT NOT NULL,
    reader_id BIGINT NOT NULL,
    qualified_at DATETIME(6) NOT NULL,
    PRIMARY KEY (post_id, reader_id),
    CONSTRAINT fk_growth_read_user FOREIGN KEY (reader_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 新系统从真实零余额开始，不依据历史帖子数或签到天数推测经验。
INSERT INTO growth_accounts(user_id, community_id) SELECT id, 0 FROM users;
INSERT INTO growth_accounts(user_id, community_id) SELECT user_id, community_id FROM community_members;
