-- 机器人身份与权限角色分开，普通注册只能创建 HUMAN。
-- 原版失败可能已提交此列；核查后的 Flyway repair 重跑不应再次因重复列失败。
SET @lumi_account_type_ddl = IF(
 EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='users' AND column_name='account_type'),
 'SELECT 1',
 'ALTER TABLE users ADD COLUMN account_type VARCHAR(16) NOT NULL DEFAULT ''HUMAN'''
);
PREPARE lumi_account_type_statement FROM @lumi_account_type_ddl;
EXECUTE lumi_account_type_statement;
DEALLOCATE PREPARE lumi_account_type_statement;
-- 内部标识含普通注册不允许的点号；展示名与 @Lumi 意图保持不变，不占用历史普通用户名。
INSERT INTO users(id,username,display_name,password_hash,avatar_url,bio,join_date,role,is_banned,account_type)
VALUES(880000000000000001,'lumi.bot','Lumi','!INTERNAL_BOT_NO_LOGIN!',
 '/emotes/cats/happy.webp','我是 Lumi，明确 @Lumi 可以围绕帖子提问。','2026-10','USER',FALSE,'BOT');

-- 低额度任务共用一个短事务协调行，外部请求期间不持有此锁。
CREATE TABLE lumi_control(id INT PRIMARY KEY);
INSERT INTO lumi_control(id) VALUES(1);
CREATE TABLE lumi_tasks(
 id BIGINT PRIMARY KEY,
 comment_id BIGINT NOT NULL UNIQUE,
 post_id BIGINT NOT NULL,
 user_id BIGINT NOT NULL,
 quota_day DATE NOT NULL,
 status VARCHAR(20) NOT NULL,
 accepted_at DATETIME(3),
 created_at DATETIME(3) NOT NULL,
 updated_at DATETIME(3) NOT NULL,
 lease_until DATETIME(3),
 lease_token VARCHAR(36),
 attempts INT NOT NULL DEFAULT 0,
 reply_id BIGINT,
 error_code VARCHAR(64),
 message VARCHAR(160),
 INDEX idx_lumi_user_day(user_id,quota_day,status),
 INDEX idx_lumi_post_day(post_id,quota_day,status),
 INDEX idx_lumi_due(status,created_at,id),
 INDEX idx_lumi_last_user(user_id,accepted_at)
);
CREATE TABLE lumi_budget_days(
 budget_day DATE PRIMARY KEY,
 calls INT NOT NULL DEFAULT 0,
 reserved_micros BIGINT NOT NULL DEFAULT 0,
 spent_micros BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE lumi_attempts(
 id BIGINT PRIMARY KEY,
 task_id BIGINT NOT NULL,
 sequence_no INT NOT NULL,
 budget_day DATE NOT NULL,
 reserved_micros BIGINT NOT NULL,
 charged_micros BIGINT,
 input_tokens INT,
 output_tokens INT,
 status VARCHAR(20) NOT NULL,
 created_at DATETIME(3) NOT NULL,
 finished_at DATETIME(3),
 UNIQUE KEY uk_lumi_attempt(task_id,sequence_no),
 INDEX idx_lumi_attempt_pending(status,created_at)
);
