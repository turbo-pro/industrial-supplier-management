CREATE TABLE sup_restriction_appeal (
 id BIGINT NOT NULL, tenant_id BIGINT NOT NULL, case_id BIGINT NOT NULL,
 reason VARCHAR(2000) NOT NULL, evidence_file_id BIGINT NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'SUBMITTED',
 created_by BIGINT NOT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 reviewed_by BIGINT NULL, reviewed_at DATETIME(3) NULL, review_comment VARCHAR(2000) NULL,
 version INT NOT NULL DEFAULT 0,
 pending_case_id BIGINT GENERATED ALWAYS AS (CASE WHEN status='SUBMITTED' THEN case_id ELSE NULL END) STORED,
 PRIMARY KEY(id), UNIQUE KEY uk_pending_appeal(tenant_id,pending_case_id),
 KEY idx_appeal_case(tenant_id,case_id,created_at,id),
 CONSTRAINT fk_appeal_tenant FOREIGN KEY(tenant_id) REFERENCES iam_tenant(id),
 CONSTRAINT fk_appeal_case FOREIGN KEY(case_id) REFERENCES sup_blacklist_case(id),
 CONSTRAINT fk_appeal_evidence FOREIGN KEY(evidence_file_id) REFERENCES res_file_object(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO sys_permission(id,permission_code,permission_name,permission_type,status) VALUES
 (7001,'supplier:appeal:view','查看限制申诉','ACTION','ACTIVE'),
 (7002,'supplier:appeal:create','登记限制申诉','ACTION','ACTIVE'),
 (7003,'supplier:appeal:review','独立复核限制申诉','ACTION','ACTIVE');
