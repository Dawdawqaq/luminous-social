-- 当前私有对话不复用公开评论；新对话擦除旧正文，额度与费用事实继续保留。
CREATE TABLE lumi_chat_sessions (
 user_id BIGINT PRIMARY KEY,
 session_id BIGINT NOT NULL,
 updated_at DATETIME(3) NOT NULL,
 UNIQUE KEY uk_lumi_chat_session(session_id)
);
CREATE TABLE lumi_chat_turns (
 id BIGINT PRIMARY KEY,
 user_id BIGINT NOT NULL,
 session_id BIGINT NOT NULL,
 request_key VARCHAR(64) NOT NULL,
 question VARCHAR(1000) NOT NULL,
 answer TEXT,
 status VARCHAR(20) NOT NULL,
 message VARCHAR(160),
 quota_day DATE NOT NULL,
 accepted_at DATETIME(3) NOT NULL,
 created_at DATETIME(3) NOT NULL,
 updated_at DATETIME(3) NOT NULL,
 lease_until DATETIME(3),
 lease_token VARCHAR(36),
 attempts INT NOT NULL DEFAULT 0,
 UNIQUE KEY uk_lumi_chat_request(user_id,request_key),
 INDEX idx_lumi_chat_history(user_id,session_id,id),
 INDEX idx_lumi_chat_user_day(user_id,quota_day,status),
 INDEX idx_lumi_chat_pending(status,created_at,id)
);
