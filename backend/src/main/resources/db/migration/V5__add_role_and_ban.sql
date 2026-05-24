-- 为 users 表新增角色字段（USER / ADMIN）与封禁状态字段
ALTER TABLE users
    ADD COLUMN role        VARCHAR(16)  NOT NULL DEFAULT 'USER'  COMMENT '用户角色: USER | ADMIN',
    ADD COLUMN is_banned   TINYINT(1)   NOT NULL DEFAULT 0        COMMENT '是否已被封禁: 0=正常, 1=封禁';

-- 将第一个种子用户（felix_the_maker）提升为系统管理员，便于本地测试
UPDATE users SET role = 'ADMIN' WHERE username = 'felix_the_maker';
