-- 为 posts 表新增 status 字段，记录帖子生命周期状态
ALTER TABLE posts
    ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED'
        COMMENT '帖子状态: PUBLISHED | BANNED';

-- 将所有现存帖子默认设为已发布
UPDATE posts SET status = 'PUBLISHED' WHERE status IS NULL OR status = '';
