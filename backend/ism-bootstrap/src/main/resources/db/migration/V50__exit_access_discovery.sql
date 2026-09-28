ALTER TABLE sup_exit_access_recovery_task
 DROP CHECK chk_exit_access_status,
 ADD COLUMN finding VARCHAR(16) NULL,
 ADD COLUMN evidence_file_id BIGINT NULL,
 ADD COLUMN discovery_note VARCHAR(1000) NULL,
 ADD COLUMN discovered_by BIGINT NULL,
 ADD COLUMN discovered_at DATETIME(3) NULL,
 ADD COLUMN version INT NOT NULL DEFAULT 0,
 ADD COLUMN updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 ADD CONSTRAINT fk_exit_access_evidence FOREIGN KEY(evidence_file_id) REFERENCES res_file_object(id),
 ADD CONSTRAINT chk_exit_access_status CHECK(
   (status='DISCOVERY_REQUIRED' AND finding IS NULL AND evidence_file_id IS NULL AND discovered_by IS NULL)
   OR (status='DISCOVERY_RECORDED' AND finding IN ('PRESENT','ABSENT') AND evidence_file_id IS NOT NULL AND discovered_by IS NOT NULL)
 );

CREATE TABLE sup_exit_access_recovery_event (
 id BIGINT NOT NULL,tenant_id BIGINT NOT NULL,task_id BIGINT NOT NULL,
 finding VARCHAR(16) NOT NULL,evidence_file_id BIGINT NOT NULL,
 note VARCHAR(1000) NOT NULL,actor_id BIGINT NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id),KEY idx_exit_access_event(tenant_id,task_id,created_at,id),
 CONSTRAINT fk_exit_access_event_task FOREIGN KEY(task_id) REFERENCES sup_exit_access_recovery_task(id),
 CONSTRAINT fk_exit_access_event_file FOREIGN KEY(evidence_file_id) REFERENCES res_file_object(id),
 CONSTRAINT chk_exit_access_finding CHECK(finding IN ('PRESENT','ABSENT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
