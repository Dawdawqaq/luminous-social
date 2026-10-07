-- 当前关注关系拉取公开帖子，创建时间和主键保证同毫秒排序稳定。
CREATE INDEX idx_posts_feed_cursor ON posts(is_public, created_at DESC, id DESC, author_id);
