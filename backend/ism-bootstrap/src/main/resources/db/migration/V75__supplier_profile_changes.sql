CREATE TABLE sup_profile_change (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, supplier_id BIGINT NOT NULL,
 request_key CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 base_version INT NOT NULL, before_json JSON NOT NULL, after_json JSON NOT NULL,
 reason VARCHAR(1000) NOT NULL, status VARCHAR(16) NOT NULL DEFAULT 'SUBMITTED',
 created_by BIGINT NOT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 reviewed_by BIGINT NULL, reviewed_at DATETIME(3) NULL, review_comment VARCHAR(1000) NULL,
 version INT NOT NULL DEFAULT 0,
 pending_supplier_id BIGINT GENERATED ALWAYS AS (CASE WHEN status='SUBMITTED' THEN supplier_id ELSE NULL END) STORED,
 UNIQUE KEY uk_profile_change_pending(tenant_id,pending_supplier_id),
 UNIQUE KEY uk_profile_change_key(tenant_id,supplier_id,request_key),
 KEY idx_profile_change_history(tenant_id,supplier_id,created_at,id),
 CONSTRAINT fk_profile_change_supplier FOREIGN KEY(supplier_id) REFERENCES sup_supplier(id),
 CONSTRAINT fk_profile_change_account FOREIGN KEY(created_by) REFERENCES iam_portal_account(id),
 CONSTRAINT chk_profile_change_status CHECK(status IN ('SUBMITTED','APPROVED','REJECTED','CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Assign this separate review permission explicitly; existing master editing grants do not approve portal requests.
INSERT INTO sys_permission(id,permission_code,permission_name,permission_type,status,module_code)
VALUES(6081,'supplier:change:review','审核门户企业资料变更','ACTION','ACTIVE','SUPPLIER');
