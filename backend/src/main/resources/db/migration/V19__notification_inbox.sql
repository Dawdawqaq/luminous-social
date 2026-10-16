-- 保留旧已读字段兼容既有客户端，版本水位避免读掉尚未展示的新互动。
ALTER TABLE notifications
    ADD COLUMN read_at DATETIME(6) NULL,
    ADD COLUMN deleted_at DATETIME(6) NULL,
    ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD COLUMN interaction_count BIGINT NOT NULL DEFAULT 1,
    ADD COLUMN read_version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN aggregate_key VARCHAR(140) NULL,
    ADD COLUMN post_id BIGINT NULL,
    ADD COLUMN comment_id BIGINT NULL,
    ADD UNIQUE INDEX uk_notification_aggregate (aggregate_key),
    ADD INDEX idx_notification_inbox (receiver_id, deleted_at, id DESC),
    ADD INDEX idx_notification_category (receiver_id, deleted_at, type, id DESC),
    ADD INDEX idx_notification_unread (receiver_id, deleted_at, is_read, type),
    ADD INDEX idx_notification_retention (updated_at, type);

CREATE INDEX idx_notification_latest ON notifications(receiver_id,deleted_at,updated_at DESC,id DESC);



UPDATE notifications SET updated_at=created_at, read_at=IF(is_read,created_at,NULL),
    read_version=IF(is_read,1,0), post_id=IF(target_type='POST',target_id,NULL);

-- 按事件发生日期记录不同点赞用户，反复取消再点赞不增加聚合人数。
CREATE TABLE notification_like_receipts (
    receiver_id BIGINT NOT NULL,
    type VARCHAR(32) NOT NULL,
    target_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    bucket_date DATE NOT NULL,
    PRIMARY KEY(receiver_id,type,target_id,bucket_date,actor_id),
    INDEX idx_like_receipt_actor(actor_id),
    INDEX idx_like_receipt_retention(bucket_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
