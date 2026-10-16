-- 已执行的迁移保持不变，分类列表补充更新时间排序索引。
ALTER TABLE notifications DROP INDEX idx_notification_category,
    ADD INDEX idx_notification_category(receiver_id,deleted_at,type,updated_at DESC,id DESC);
