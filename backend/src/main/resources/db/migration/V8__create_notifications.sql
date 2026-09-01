-- V8: 创建通知消息中心表 (notifications)
CREATE TABLE IF NOT EXISTS notifications (
    id                BIGINT       PRIMARY KEY COMMENT '通知唯一ID (雪花算法/IdWorker)',
    receiver_id       BIGINT       NOT NULL COMMENT '接收者用户ID',
    actor_id          BIGINT       NOT NULL COMMENT '动作发起者用户ID',
    type              VARCHAR(32)  NOT NULL COMMENT '事件类型: LIKE_POST | COMMENT_POST | REPLY_COMMENT | FOLLOW_USER | SYSTEM',
    target_type       VARCHAR(32)  NOT NULL COMMENT '关联主体类型: POST | COMMENT | USER | COMMUNITY',
    target_id         BIGINT       NOT NULL COMMENT '关联主体ID (如 postId, commentId)',
    content_preview   VARCHAR(255) NULL     COMMENT '简要上下文快照 (如帖子标题、评论摘录)',
    is_read           BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '是否已读: 0=未读, 1=已读',
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '触发时间',
    INDEX idx_receiver_read (receiver_id, is_read),
    INDEX idx_receiver_created (receiver_id, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
