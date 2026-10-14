-- 使用前向迁移补齐分页索引，兼容已执行旧开发迁移的数据库。
SET @follow_index_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'user_follows'
             AND index_name = 'idx_follows_followers_time'),
    'SELECT 1',
    'CREATE INDEX idx_follows_followers_time ON user_follows(following_id, created_at, follower_id)'
);
PREPARE follow_index_statement FROM @follow_index_sql;
EXECUTE follow_index_statement;
DEALLOCATE PREPARE follow_index_statement;

SET @follow_index_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'user_follows'
             AND index_name = 'idx_follows_following_time'),
    'SELECT 1',
    'CREATE INDEX idx_follows_following_time ON user_follows(follower_id, created_at, following_id)'
);
PREPARE follow_index_statement FROM @follow_index_sql;
EXECUTE follow_index_statement;
DEALLOCATE PREPARE follow_index_statement;
