CREATE TABLE qua_portal_renewal (
 qualification_id BIGINT NOT NULL,tenant_id BIGINT NOT NULL,supplier_id BIGINT NOT NULL,
 parent_id BIGINT NOT NULL,parent_version INT NOT NULL,state VARCHAR(20) NOT NULL DEFAULT 'PENDING',
 pending_parent_id BIGINT GENERATED ALWAYS AS(CASE WHEN state='PENDING' THEN parent_id ELSE NULL END) STORED,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 PRIMARY KEY(qualification_id),UNIQUE KEY uk_portal_pending_renewal(tenant_id,pending_parent_id),
 KEY idx_portal_renewal_parent(tenant_id,supplier_id,parent_id,created_at),
 CONSTRAINT fk_portal_renewal_origin FOREIGN KEY(qualification_id) REFERENCES qua_portal_submission(qualification_id),
 CONSTRAINT fk_portal_renewal_parent FOREIGN KEY(parent_id) REFERENCES qua_supplier_qualification(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
