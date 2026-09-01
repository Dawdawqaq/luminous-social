-- 1. 为 user_follows 增加倒排索引，加速“某用户的粉丝列表”及粉丝数统计查询
CREATE INDEX idx_follows_following_follower ON user_follows(following_id, follower_id);

-- 2. 为 posts 表增加社区与作者维度的 Feed 流复合索引
CREATE INDEX idx_posts_feed_community ON posts(community_id, is_public, votes, created_at);
CREATE INDEX idx_posts_feed_author ON posts(author_id, is_public, created_at);

-- 3. 为 posts 与 users 增加基于 ngram 分词器的全文检索索引，优化中文/英文混合模糊搜索
CREATE FULLTEXT INDEX ft_idx_posts_search ON posts(title, content) WITH PARSER ngram;
CREATE FULLTEXT INDEX ft_idx_users_search ON users(username, display_name) WITH PARSER ngram;
