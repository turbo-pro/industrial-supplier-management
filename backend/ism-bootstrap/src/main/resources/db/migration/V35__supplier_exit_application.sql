CREATE TABLE sup_exit_application (
 id BIGINT NOT NULL,tenant_id BIGINT NOT NULL,supplier_id BIGINT NOT NULL,
 exit_type VARCHAR(20) NOT NULL,reason VARCHAR(2000) NOT NULL,evidence_file_id BIGINT NOT NULL,
 status VARCHAR(24) NOT NULL DEFAULT 'SUBMITTED',created_by BIGINT NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 reviewed_by BIGINT NULL,reviewed_at DATETIME(3) NULL,review_comment VARCHAR(2000) NULL,
 version INT NOT NULL DEFAULT 0,
 pending_supplier_id BIGINT GENERATED ALWAYS AS (CASE WHEN status='SUBMITTED' THEN supplier_id ELSE NULL END) STORED,
 PRIMARY KEY(id),UNIQUE KEY uk_exit_pending(tenant_id,pending_supplier_id),
 KEY idx_exit_supplier(tenant_id,supplier_id,created_at,id),
 CONSTRAINT fk_exit_tenant FOREIGN KEY(tenant_id) REFERENCES iam_tenant(id),
 CONSTRAINT fk_exit_supplier FOREIGN KEY(supplier_id) REFERENCES sup_supplier(id),
 CONSTRAINT fk_exit_evidence FOREIGN KEY(evidence_file_id) REFERENCES res_file_object(id),
 CONSTRAINT chk_exit_type CHECK(exit_type IN ('NORMAL','ELIMINATION')),
 CONSTRAINT chk_exit_status CHECK(status IN ('SUBMITTED','REJECTED','CANCELLED','BUSINESS_CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sup_exit_item (
 id BIGINT NOT NULL,tenant_id BIGINT NOT NULL,application_id BIGINT NOT NULL,
 check_code VARCHAR(64) NOT NULL,check_label VARCHAR(200) NOT NULL,route VARCHAR(200) NOT NULL,
 initial_count BIGINT NOT NULL,current_count BIGINT NOT NULL,
 checked_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id),UNIQUE KEY uk_exit_item(tenant_id,application_id,check_code),
 CONSTRAINT fk_exit_item_application FOREIGN KEY(application_id) REFERENCES sup_exit_application(id),
 CONSTRAINT chk_exit_item_counts CHECK(initial_count>=0 AND current_count>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sup_exit_result (
 id BIGINT NOT NULL,tenant_id BIGINT NOT NULL,application_id BIGINT NOT NULL,supplier_id BIGINT NOT NULL,
 approved_by BIGINT NOT NULL,comment VARCHAR(2000) NOT NULL,
 completion_scope VARCHAR(32) NOT NULL DEFAULT 'LOCAL_BUSINESS',
 access_recovery_status VARCHAR(32) NOT NULL DEFAULT 'NOT_VERIFIED',
 effective_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id),UNIQUE KEY uk_exit_result_application(tenant_id,application_id),
 UNIQUE KEY uk_exit_result_supplier(tenant_id,supplier_id),
 CONSTRAINT fk_exit_result_application FOREIGN KEY(application_id) REFERENCES sup_exit_application(id),
 CONSTRAINT fk_exit_result_supplier FOREIGN KEY(supplier_id) REFERENCES sup_supplier(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sup_exit_event (
 id BIGINT NOT NULL,tenant_id BIGINT NOT NULL,application_id BIGINT NOT NULL,
 action VARCHAR(32) NOT NULL,comment VARCHAR(2000) NULL,actor_id BIGINT NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id),KEY idx_exit_event(tenant_id,application_id,created_at,id),
 CONSTRAINT fk_exit_event_application FOREIGN KEY(application_id) REFERENCES sup_exit_application(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO sys_permission(id,permission_code,permission_name,permission_type,status) VALUES
 (7201,'supplier:exit:view','查看退出申请与处置项','ACTION','ACTIVE'),
 (7202,'supplier:exit:create','发起供应商退出申请','ACTION','ACTIVE'),
 (7203,'supplier:exit:recheck','重新核验退出处置项','ACTION','ACTIVE'),
 (7204,'supplier:exit:review','独立审批供应商退出','ACTION','ACTIVE'),
 (7205,'supplier:exit:cancel','撤回本人退出申请','ACTION','ACTIVE');
