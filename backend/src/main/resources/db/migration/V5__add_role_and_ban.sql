-- 为 users 表新增角色字段（USER / ADMIN）与封禁状态字段
ALTER TABLE users
    ADD COLUMN role        VARCHAR(16)  NOT NULL DEFAULT 'USER'  COMMENT '用户角色: USER | ADMIN',
    ADD COLUMN is_banned   TINYINT(1)   NOT NULL DEFAULT 0        COMMENT '是否已被封禁: 0=正常, 1=封禁';

