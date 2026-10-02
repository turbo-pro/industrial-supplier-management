CREATE TABLE sup_exit_access_principal (
 id BIGINT NOT NULL,
 tenant_id BIGINT NOT NULL,
 task_id BIGINT NOT NULL,
 external_reference VARCHAR(128) NOT NULL,
 display_label VARCHAR(120) NOT NULL,
 evidence_file_id BIGINT NOT NULL,
 note VARCHAR(1000) NOT NULL,
 recorded_by BIGINT NOT NULL,
 recorded_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id),
 UNIQUE KEY uk_exit_access_principal(tenant_id,task_id,external_reference),
 KEY idx_exit_access_principal_task(tenant_id,task_id,recorded_at,id),
 CONSTRAINT fk_exit_access_principal_task FOREIGN KEY(task_id) REFERENCES sup_exit_access_recovery_task(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
