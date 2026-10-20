ALTER TABLE sup_portal_invitation ADD COLUMN accepted_at DATETIME(3) NULL,
    ADD COLUMN accepted_account_id BIGINT NULL;

CREATE TABLE iam_portal_account (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    supplier_id BIGINT NOT NULL,
    username VARCHAR(64) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    token_version INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_portal_account_username (tenant_id,username),
    UNIQUE KEY uk_portal_account_supplier_email (tenant_id,supplier_id,email),
    CONSTRAINT fk_portal_account_tenant FOREIGN KEY (tenant_id) REFERENCES iam_tenant(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sup_portal_registration_rate_bucket (
    source_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    window_minute BIGINT NOT NULL,
    attempts INT NOT NULL,
    PRIMARY KEY (source_hash,window_minute)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
