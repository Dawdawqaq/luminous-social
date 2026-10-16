-- 独立标题全文索引保证标题命中先于正文；前缀查询使用普通索引。
CREATE FULLTEXT INDEX ft_idx_posts_title ON posts(title) WITH PARSER ngram;
CREATE INDEX idx_posts_search_title ON posts(title, id);
CREATE INDEX idx_users_search_display ON users(display_name, id);
CREATE INDEX idx_communities_search_name ON communities(name, id);
CREATE FULLTEXT INDEX ft_idx_communities_search ON communities(slug, name, description) WITH PARSER ngram;
