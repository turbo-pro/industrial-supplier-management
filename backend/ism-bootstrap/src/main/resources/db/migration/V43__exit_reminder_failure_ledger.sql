CREATE TABLE sup_exit_reminder_failure (
 tenant_id BIGINT NOT NULL,
 entity_id BIGINT NOT NULL,
 application_id BIGINT NOT NULL,
 supplier_id BIGINT NOT NULL,
 failure_count INT NOT NULL DEFAULT 1,
 reason_code VARCHAR(64) NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'FAILED',
 first_failed_at DATETIME(3) NOT NULL,
 last_failed_at DATETIME(3) NOT NULL,
 resolved_at DATETIME(3) NULL,
 PRIMARY KEY(tenant_id,entity_id),
 KEY idx_exit_reminder_failure_supplier(tenant_id,supplier_id,last_failed_at,entity_id),
 CONSTRAINT fk_exit_reminder_failure_tenant FOREIGN KEY(tenant_id) REFERENCES iam_tenant(id),
 CONSTRAINT fk_exit_reminder_failure_entity FOREIGN KEY(entity_id) REFERENCES sup_exit_entity(id),
 CONSTRAINT chk_exit_reminder_failure_count CHECK(failure_count>0),
 CONSTRAINT chk_exit_reminder_failure_status CHECK(status IN ('FAILED','DELIVERED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
