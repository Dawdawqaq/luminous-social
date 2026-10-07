-- 幂等结果与业务更新共用事务，未提交的操作不会留下成功凭证。
-- 操作标识按字节区分大小写，不能被数据库默认不区分大小写的排序规则合并。
CREATE TABLE idempotent_requests (
    scope_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    operation_key VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(16) NOT NULL,
    response_status INT,
    content_type VARCHAR(128),
    response_body MEDIUMTEXT,
    expires_at TIMESTAMP NOT NULL,
    PRIMARY KEY (scope_hash, operation_key),
    INDEX idx_idempotent_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 管理员初始化固定行串行化，不依据公开注册请求抢占管理员身份。
CREATE TABLE system_bootstrap (
    id INT PRIMARY KEY,
    admin_user_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
INSERT INTO system_bootstrap(id) VALUES(1);

-- 写文件前独立持久化清理意图，数据库回滚或进程中断后仍可恢复。
CREATE TABLE storage_operations (
    id BIGINT PRIMARY KEY,
    object_key VARCHAR(255) NOT NULL,
    storage_type VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    lease_until TIMESTAMP NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    last_error VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_storage_pending (status, lease_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
ALTER TABLE uploaded_files ADD COLUMN storage_type VARCHAR(16);
ALTER TABLE uploaded_files ADD COLUMN read_token_hash CHAR(64);
CREATE INDEX idx_uploaded_stored_name ON uploaded_files(stored_name);
