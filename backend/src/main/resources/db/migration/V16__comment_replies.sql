-- 楼内回复直接指向主评论；原回复及用户编号用于标识回复对象。
-- 不对主评论设置级联外键，删除主评论后保留其他用户的楼内回复。
ALTER TABLE comments
    ADD COLUMN parent_id BIGINT NULL AFTER post_id,
    ADD COLUMN reply_to_comment_id BIGINT NULL AFTER parent_id,
    ADD COLUMN reply_to_user_id BIGINT NULL AFTER reply_to_comment_id,
    ADD INDEX idx_comment_floor (post_id, parent_id, created_at, id);
