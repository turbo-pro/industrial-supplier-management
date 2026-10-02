ALTER TABLE sup_exit_access_principal
 ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
 ADD COLUMN version INT NOT NULL DEFAULT 0,
 ADD COLUMN void_reason VARCHAR(1000) NULL,
 ADD COLUMN void_evidence_file_id BIGINT NULL,
 ADD COLUMN voided_by BIGINT NULL,
 ADD COLUMN voided_at DATETIME(3) NULL,
 DROP INDEX uk_exit_access_principal,
 ADD COLUMN active_external_reference VARCHAR(128)
   GENERATED ALWAYS AS (CASE WHEN status='ACTIVE' THEN external_reference ELSE NULL END) STORED,
 ADD UNIQUE KEY uk_exit_access_principal_active(tenant_id,task_id,active_external_reference),
 ADD CONSTRAINT chk_exit_access_principal_status CHECK(status IN ('ACTIVE','VOIDED'));
