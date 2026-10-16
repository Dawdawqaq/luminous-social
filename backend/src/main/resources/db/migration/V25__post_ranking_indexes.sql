-- 限定近七天公开可见帖子，避免定期榜单聚合扫描全部历史内容。
CREATE INDEX idx_posts_ranking_window ON posts (is_public, status, created_at, id);
-- 讨论贡献按参与用户去重，不按评论条数放大热度。
CREATE INDEX idx_comments_post_author ON comments (post_id, author_id);
