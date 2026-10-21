ALTER TABLE iam_portal_account
    ADD COLUMN failed_count INT NOT NULL DEFAULT 0,
    ADD COLUMN locked_until DATETIME(3) NULL,
    ADD COLUMN last_login_at DATETIME(3) NULL;

CREATE TABLE iam_portal_session (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    token_version INT NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    revoked_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_portal_session_hash (token_hash),
    KEY idx_portal_session_account (tenant_id,account_id),
    CONSTRAINT fk_portal_session_account FOREIGN KEY (account_id) REFERENCES iam_portal_account(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
