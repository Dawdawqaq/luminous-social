-- 分类从创建表单持久化；旧社区归入生活兴趣，按编号游标浏览。
ALTER TABLE communities ADD COLUMN category VARCHAR(32) NOT NULL DEFAULT 'Living';
CREATE INDEX idx_communities_category_id ON communities(category, id);

-- 成功发布额度不依赖帖子存活；事务失败自动回滚，删除帖子不返还额度。
CREATE TABLE post_publish_gates (
    user_id BIGINT PRIMARY KEY,
    last_published_at DATETIME(6) NULL,
    CONSTRAINT fk_post_gate_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE post_publications (
    post_id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    published_at DATETIME(6) NOT NULL,
    INDEX idx_post_publication_window(user_id, published_at),
    CONSTRAINT fk_post_publication_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
