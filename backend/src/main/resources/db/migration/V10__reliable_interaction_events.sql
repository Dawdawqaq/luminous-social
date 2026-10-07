-- 投票接收水位与当前意图；取消状态保留为零值墓碑，防止旧消息复活。
CREATE TABLE post_vote_clocks (
    post_id BIGINT PRIMARY KEY,
    accepted_version BIGINT NOT NULL DEFAULT 0,
    accepted_total INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_vote_clock_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE
) ENGINE=InnoDB;
CREATE TABLE vote_states (
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    desired_value INT NOT NULL,
    accepted_version BIGINT NOT NULL,
    applied_value INT NOT NULL,
    applied_version BIGINT NOT NULL,
    PRIMARY KEY (post_id, user_id),
    CONSTRAINT fk_vote_state_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE,
    CONSTRAINT fk_vote_state_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;
-- 已有投票作为版本零的基线，不依赖 Redis 是否还保存旧状态。
INSERT INTO post_vote_clocks(post_id, accepted_version, accepted_total)
SELECT id, 0, COALESCE((SELECT SUM(value) FROM post_votes WHERE post_id=posts.id), 0) FROM posts;
INSERT INTO vote_states(post_id, user_id, desired_value, accepted_version, applied_value, applied_version)
SELECT post_id, user_id, value, 0, value, 0 FROM post_votes;

-- 待投递事件与业务意图同事务提交；发送成功后仍保留，直到消费事务提交。
CREATE TABLE reliable_events (
    event_id VARCHAR(96) PRIMARY KEY,
    event_type VARCHAR(24) NOT NULL,
    post_id BIGINT,
    user_id BIGINT,
    business_version BIGINT NOT NULL DEFAULT 0,
    payload TEXT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_event_retry(status, next_attempt_at),
    INDEX idx_event_vote(post_id, user_id, business_version)
) ENGINE=InnoDB;
CREATE TABLE consumed_events (
    consumer_name VARCHAR(32) NOT NULL,
    event_id VARCHAR(96) NOT NULL,
    consumed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (consumer_name, event_id)
) ENGINE=InnoDB;
CREATE TABLE failed_messages (
    id BIGINT PRIMARY KEY,
    consumer_name VARCHAR(32) NOT NULL,
    event_id VARCHAR(96) NOT NULL,
    payload TEXT NOT NULL,
    attempts INT NOT NULL,
    last_error VARCHAR(1000) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'FAILED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_failed_event(consumer_name, event_id),
    INDEX idx_failed_query(status, updated_at)
) ENGINE=InnoDB;
