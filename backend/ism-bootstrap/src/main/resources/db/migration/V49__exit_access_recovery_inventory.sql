CREATE TABLE sup_exit_access_recovery_task (
 id BIGINT NOT NULL,
 tenant_id BIGINT NOT NULL,
 application_id BIGINT NOT NULL,
 supplier_id BIGINT NOT NULL,
 channel VARCHAR(32) NOT NULL,
 status VARCHAR(32) NOT NULL DEFAULT 'DISCOVERY_REQUIRED',
 created_by BIGINT NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id),
 UNIQUE KEY uk_exit_access_channel(tenant_id,application_id,channel),
 KEY idx_exit_access_supplier(tenant_id,supplier_id,application_id),
 CONSTRAINT fk_exit_access_tenant FOREIGN KEY(tenant_id) REFERENCES iam_tenant(id),
 CONSTRAINT fk_exit_access_application FOREIGN KEY(application_id) REFERENCES sup_exit_application(id),
 CONSTRAINT fk_exit_access_supplier FOREIGN KEY(supplier_id) REFERENCES sup_supplier(id),
 CONSTRAINT chk_exit_access_channel CHECK(channel IN ('PORTAL_ACCOUNT','DOOR_ACCESS','API_CREDENTIAL')),
 CONSTRAINT chk_exit_access_status CHECK(status='DISCOVERY_REQUIRED')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
