-- 发布额度独立于评论存活状态，删除评论不退还成功发布次数。
CREATE TABLE comment_publish_gates (
    user_id BIGINT PRIMARY KEY,
    last_published_at DATETIME(6) NULL,
    CONSTRAINT fk_comment_gate_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE comment_publications (
    comment_id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    published_at DATETIME(6) NOT NULL,
    INDEX idx_comment_publication_window (user_id, published_at),
    CONSTRAINT fk_comment_publication_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
