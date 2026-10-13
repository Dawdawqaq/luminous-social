-- 隔离投票验收结构，基于迁移 V1、V3 至 V6、V8、V9；不包含 MySQL 全文索引。
DROP ALL OBJECTS;
CREATE TABLE users (
    id BIGINT PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    display_name VARCHAR(96) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    avatar_url VARCHAR(512),
    banner_url VARCHAR(512),
    bio VARCHAR(512),
    join_date VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE communities (
    id BIGINT PRIMARY KEY,
    slug VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(96) NOT NULL,
    icon_url VARCHAR(512),
    banner_url VARCHAR(512),
    description VARCHAR(1024) NOT NULL,
    members_count INT NOT NULL DEFAULT 0,
    members_unit VARCHAR(32) NOT NULL DEFAULT 'members',
    rules_json JSON NOT NULL,
    moderators_json JSON NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE community_members (
    id BIGINT PRIMARY KEY,
    community_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_community_user (community_id, user_id),
    CONSTRAINT fk_member_community FOREIGN KEY (community_id) REFERENCES communities(id) ON DELETE CASCADE,
    CONSTRAINT fk_member_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE posts (
    id BIGINT PRIMARY KEY,
    author_id BIGINT NOT NULL,
    community_id BIGINT NOT NULL,
    title VARCHAR(180) NOT NULL,
    content TEXT,
    image_url VARCHAR(512),
    post_type VARCHAR(32) NOT NULL DEFAULT 'standard',
    votes INT NOT NULL DEFAULT 0,
    comments_count INT NOT NULL DEFAULT 0,
    is_public BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_post_author FOREIGN KEY (author_id) REFERENCES users(id),
    CONSTRAINT fk_post_community FOREIGN KEY (community_id) REFERENCES communities(id),
    INDEX idx_post_created_at (created_at),
    INDEX idx_post_votes (votes)
);

CREATE TABLE comments (
    id BIGINT PRIMARY KEY,
    post_id BIGINT NOT NULL,
    parent_id BIGINT,
    reply_to_comment_id BIGINT,
    reply_to_user_id BIGINT,
    author_id BIGINT NOT NULL,
    content VARCHAR(1000) NOT NULL,
    image_url VARCHAR(512),
    votes INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_comment_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE,
    CONSTRAINT fk_comment_author FOREIGN KEY (author_id) REFERENCES users(id),
    INDEX idx_comment_post_created_at (post_id, created_at)
);

CREATE TABLE post_votes (
    id BIGINT PRIMARY KEY,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    value INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_post_vote_user (post_id, user_id),
    CONSTRAINT fk_post_vote_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE,
    CONSTRAINT fk_post_vote_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE comment_votes (
    id BIGINT PRIMARY KEY,
    comment_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    value INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_comment_vote_user (comment_id, user_id),
    CONSTRAINT fk_comment_vote_comment FOREIGN KEY (comment_id) REFERENCES comments(id) ON DELETE CASCADE,
    CONSTRAINT fk_comment_vote_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE bookmarks (
    id BIGINT PRIMARY KEY,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_bookmark_user_post (user_id, post_id),
    CONSTRAINT fk_bookmark_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE,
    CONSTRAINT fk_bookmark_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE uploaded_files (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    stored_name VARCHAR(255) NOT NULL,
    url VARCHAR(512) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    size_bytes BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_uploaded_file_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

ALTER TABLE communities ADD COLUMN owner_id BIGINT;

CREATE TABLE user_follows (
    follower_id BIGINT NOT NULL,
    following_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (follower_id, following_id)
);

-- 为 users 表新增角色字段（USER / ADMIN）与封禁状态字段
ALTER TABLE users
    ADD COLUMN role        VARCHAR(16)  NOT NULL DEFAULT 'USER';
ALTER TABLE users ADD COLUMN is_banned   TINYINT(1)   NOT NULL DEFAULT 0       ;


-- 为 posts 表新增 status 字段，记录帖子生命周期状态
ALTER TABLE posts
    ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED'
       ;

-- 将所有现存帖子默认设为已发布
UPDATE posts SET status = 'PUBLISHED' WHERE status IS NULL OR status = '';

-- V8: 创建通知消息中心表 (notifications)
CREATE TABLE IF NOT EXISTS notifications (
    id                BIGINT       PRIMARY KEY,
    receiver_id       BIGINT       NOT NULL,
    actor_id          BIGINT       NOT NULL,
    type              VARCHAR(32)  NOT NULL,
    target_type       VARCHAR(32)  NOT NULL,
    target_id         BIGINT       NOT NULL,
    content_preview   VARCHAR(255) NULL    ,
    is_read           BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_receiver_read (receiver_id, is_read),
    INDEX idx_receiver_created (receiver_id, created_at DESC)
);

-- 社区签到记录表（Redis Bitmap 为主存储，此表作为历史归档与持久化备份）
CREATE TABLE IF NOT EXISTS community_checkins (
    id BIGINT PRIMARY KEY,
    community_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    checkin_date DATE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_checkin (community_id, user_id, checkin_date),
    CONSTRAINT fk_checkin_community FOREIGN KEY (community_id) REFERENCES communities(id) ON DELETE CASCADE,
    CONSTRAINT fk_checkin_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_checkin_user_date (user_id, checkin_date)
);

-- 投票接收水位与当前意图；取消状态保留为零值墓碑，防止旧消息复活。
CREATE TABLE post_vote_clocks (
    post_id BIGINT PRIMARY KEY,
    accepted_version BIGINT NOT NULL DEFAULT 0,
    accepted_total INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_vote_clock_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE
);
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
);
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
);
CREATE TABLE consumed_events (
    consumer_name VARCHAR(32) NOT NULL,
    event_id VARCHAR(96) NOT NULL,
    consumed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (consumer_name, event_id)
);
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
);

CREATE INDEX idx_posts_feed_cursor ON posts(is_public, created_at DESC, id DESC, author_id);

-- 幂等结果与业务更新共用事务，未提交的操作不会留下成功凭证。
CREATE TABLE idempotent_requests (
    scope_hash CHAR(64) NOT NULL,
    operation_key VARCHAR(96) NOT NULL,
    fingerprint CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    response_status INT,
    content_type VARCHAR(128),
    response_body MEDIUMTEXT,
    expires_at TIMESTAMP NOT NULL,
    PRIMARY KEY (scope_hash, operation_key),
    INDEX idx_idempotent_expiry (expires_at)
);

-- 管理员初始化固定行串行化，不依据公开注册请求抢占管理员身份。
CREATE TABLE system_bootstrap (
    id INT PRIMARY KEY,
    admin_user_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO system_bootstrap(id) VALUES(1);

-- 写文件前独立持久化清理意图，数据库回滚或进程中断后仍可恢复。
CREATE TABLE storage_operations (
    id BIGINT PRIMARY KEY,
    object_key VARCHAR(255) NOT NULL,
    storage_type VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    lease_until TIMESTAMP NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    last_error VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_storage_pending (status, lease_until)
);
ALTER TABLE uploaded_files ADD COLUMN storage_type VARCHAR(16);
ALTER TABLE uploaded_files ADD COLUMN read_token_hash CHAR(64);
CREATE INDEX idx_uploaded_stored_name ON uploaded_files(stored_name);
