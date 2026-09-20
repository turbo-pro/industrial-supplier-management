CREATE TABLE iam_tenant (
    id BIGINT NOT NULL,
    tenant_code VARCHAR(64) NOT NULL,
    tenant_name VARCHAR(200) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_iam_tenant_code (tenant_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_user (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    username VARCHAR(100) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    status VARCHAR(32) NOT NULL,
    force_password_change TINYINT NOT NULL DEFAULT 0,
    password_changed_at DATETIME(3) NULL,
    last_login_at DATETIME(3) NULL,
    locked_until DATETIME(3) NULL,
    failed_count INT NOT NULL DEFAULT 0,
    token_version INT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_iam_user_name (tenant_id, username, deleted),
    KEY idx_iam_user_tenant_status (tenant_id, status),
    CONSTRAINT fk_iam_user_tenant FOREIGN KEY (tenant_id) REFERENCES iam_tenant (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_refresh_token (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    token_hash CHAR(64) NOT NULL,
    family_id CHAR(36) NOT NULL,
    issued_at DATETIME(3) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    revoked_at DATETIME(3) NULL,
    revoke_reason VARCHAR(100) NULL,
    replaced_by_hash CHAR(64) NULL,
    device_id VARCHAR(128) NOT NULL,
    ip VARCHAR(64) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_iam_refresh_token_hash (token_hash),
    KEY idx_iam_refresh_family (family_id),
    KEY idx_iam_refresh_user (tenant_id, user_id),
    CONSTRAINT fk_iam_refresh_tenant FOREIGN KEY (tenant_id) REFERENCES iam_tenant (id),
    CONSTRAINT fk_iam_refresh_user FOREIGN KEY (user_id) REFERENCES iam_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
