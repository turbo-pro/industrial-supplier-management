CREATE TABLE sup_lift_application (
 id BIGINT NOT NULL,tenant_id BIGINT NOT NULL,case_id BIGINT NOT NULL,
 reason VARCHAR(2000) NOT NULL,evidence_file_id BIGINT NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'SUBMITTED',created_by BIGINT NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 reviewed_by BIGINT NULL,reviewed_at DATETIME(3) NULL,review_comment VARCHAR(2000) NULL,
 version INT NOT NULL DEFAULT 0,
 pending_case_id BIGINT GENERATED ALWAYS AS (CASE WHEN status='SUBMITTED' THEN case_id ELSE NULL END) STORED,
 PRIMARY KEY(id),UNIQUE KEY uk_pending_lift(tenant_id,pending_case_id),
 KEY idx_lift_case(tenant_id,case_id,created_at,id),
 CONSTRAINT fk_lift_tenant FOREIGN KEY(tenant_id) REFERENCES iam_tenant(id),
 CONSTRAINT fk_lift_case FOREIGN KEY(case_id) REFERENCES sup_blacklist_case(id),
 CONSTRAINT fk_lift_file FOREIGN KEY(evidence_file_id) REFERENCES res_file_object(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE sup_lift_result (
 id BIGINT NOT NULL,tenant_id BIGINT NOT NULL,case_id BIGINT NOT NULL,application_id BIGINT NOT NULL,
 approved_by BIGINT NOT NULL,reason VARCHAR(2000) NOT NULL,
 effective_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id),UNIQUE KEY uk_lift_result_case(tenant_id,case_id),
 UNIQUE KEY uk_lift_result_application(tenant_id,application_id),
 CONSTRAINT fk_lift_result_case FOREIGN KEY(case_id) REFERENCES sup_blacklist_case(id),
 CONSTRAINT fk_lift_result_application FOREIGN KEY(application_id) REFERENCES sup_lift_application(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
INSERT INTO sys_permission(id,permission_code,permission_name,permission_type,status) VALUES
 (7101,'supplier:lift:view','查看解除申请与核验','ACTION','ACTIVE'),
 (7102,'supplier:lift:create','发起独立解除申请','ACTION','ACTIVE'),
 (7103,'supplier:lift:review','审批限制解除','ACTION','ACTIVE');
