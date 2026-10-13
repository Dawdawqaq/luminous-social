-- 为评论与楼内回复提供票数、时间和编号的联合索引。
CREATE INDEX idx_comment_rank ON comments (post_id, parent_id, votes DESC, created_at ASC, id ASC);
