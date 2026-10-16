-- 会话实体独立于旧的当前会话指针，保留旧编号和消息；已擦除的历史不能凭空恢复。
CREATE TABLE lumi_chat_conversations (
 id BIGINT PRIMARY KEY,
 user_id BIGINT NOT NULL,
 title VARCHAR(40) NOT NULL,
 title_custom BOOLEAN NOT NULL DEFAULT FALSE,
 create_key VARCHAR(64),
 created_at DATETIME(3) NOT NULL,
 activity_at DATETIME(3) NOT NULL,
 deleted_at DATETIME(3),
 UNIQUE KEY uk_lumi_conversation_create(user_id,create_key),
 INDEX idx_lumi_conversation_list(user_id,deleted_at,activity_at DESC,id DESC)
);
INSERT INTO lumi_chat_conversations(id,user_id,title,created_at,activity_at)
SELECT s.session_id,s.user_id,
 COALESCE(NULLIF(LEFT((SELECT t.question FROM lumi_chat_turns t WHERE t.user_id=s.user_id AND t.session_id=s.session_id AND t.question<>'' ORDER BY t.id LIMIT 1),24),''),'新对话'),
 COALESCE((SELECT MIN(t.created_at) FROM lumi_chat_turns t WHERE t.user_id=s.user_id AND t.session_id=s.session_id),s.updated_at),
 COALESCE((SELECT MAX(t.updated_at) FROM lumi_chat_turns t WHERE t.user_id=s.user_id AND t.session_id=s.session_id),s.updated_at)
FROM lumi_chat_sessions s;
INSERT IGNORE INTO lumi_chat_conversations(id,user_id,title,created_at,activity_at)
SELECT g.session_id,g.user_id,LEFT((SELECT q.question FROM lumi_chat_turns q WHERE q.user_id=g.user_id AND q.session_id=g.session_id AND q.question<>'' ORDER BY q.id LIMIT 1),24),g.created_at,g.activity_at
FROM (SELECT session_id,user_id,MIN(created_at) created_at,MAX(updated_at) activity_at FROM lumi_chat_turns WHERE question<>'' GROUP BY user_id,session_id) g;
