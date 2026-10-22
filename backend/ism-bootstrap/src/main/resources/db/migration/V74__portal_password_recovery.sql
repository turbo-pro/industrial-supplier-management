CREATE TABLE iam_portal_password_recovery (
 id BIGINT NOT NULL PRIMARY KEY,
 tenant_id BIGINT NOT NULL,
 account_id BIGINT NOT NULL,
 token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 account_version INT NOT NULL,
 token_version INT NOT NULL,
 status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
 expires_at DATETIME(3) NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 created_by BIGINT NOT NULL,
 reason VARCHAR(500) NOT NULL,
 consumed_at DATETIME(3) NULL,
 revoked_at DATETIME(3) NULL,
 revoked_by BIGINT NULL,
 pending_account_id BIGINT GENERATED ALWAYS AS (CASE WHEN status='PENDING' THEN account_id ELSE NULL END) STORED,
 UNIQUE KEY uk_portal_recovery_hash(token_hash),
 UNIQUE KEY uk_portal_recovery_pending(tenant_id,pending_account_id),
 KEY idx_portal_recovery_account(tenant_id,account_id,id),
 CONSTRAINT fk_portal_recovery_account FOREIGN KEY(account_id) REFERENCES iam_portal_account(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Deliberately not auto-granted to existing roles. Assign explicitly after identity-verification policy is established.
INSERT INTO sys_permission(id,permission_code,permission_name,permission_type,status,module_code)
VALUES(6080,'supplier:portal:recover','核实身份并恢复门户密码','ACTION','ACTIVE','SUPPLIER');
