CREATE TABLE per_improvement_plan (
 id BIGINT NOT NULL, tenant_id BIGINT NOT NULL, evaluation_id BIGINT NOT NULL,
 root_cause VARCHAR(1000) NOT NULL, action_plan VARCHAR(2000) NOT NULL, due_date DATE NOT NULL,
 status VARCHAR(24) NOT NULL DEFAULT 'OPEN', completion_note VARCHAR(2000) NULL,
 evidence_file_id BIGINT NULL, review_comment VARCHAR(1000) NULL, reviewed_by BIGINT NULL,
 reviewed_at DATETIME(3) NULL, created_by BIGINT NOT NULL, updated_by BIGINT NOT NULL,
 version INT NOT NULL DEFAULT 0,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id), UNIQUE KEY uk_per_improvement_evaluation(tenant_id,evaluation_id),
 KEY idx_per_improvement_due(tenant_id,status,due_date),
 CONSTRAINT fk_per_improvement_evaluation FOREIGN KEY(evaluation_id) REFERENCES per_supplier_evaluation(id),
 CONSTRAINT fk_per_improvement_file FOREIGN KEY(evidence_file_id) REFERENCES res_file_object(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE per_improvement_event (
 id BIGINT NOT NULL, tenant_id BIGINT NOT NULL, plan_id BIGINT NOT NULL,
 action VARCHAR(24) NOT NULL, from_status VARCHAR(24) NULL, to_status VARCHAR(24) NOT NULL,
 comment VARCHAR(2000) NULL, actor_id BIGINT NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id), KEY idx_per_improvement_event(tenant_id,plan_id,created_at),
 CONSTRAINT fk_per_improvement_event_plan FOREIGN KEY(plan_id) REFERENCES per_improvement_plan(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
