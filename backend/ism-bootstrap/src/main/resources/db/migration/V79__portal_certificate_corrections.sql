CREATE TABLE qua_portal_correction (
 qualification_id BIGINT NOT NULL,tenant_id BIGINT NOT NULL,supplier_id BIGINT NOT NULL,
 predecessor_id BIGINT NOT NULL,predecessor_version INT NOT NULL,root_id BIGINT NOT NULL,round_no INT NOT NULL,
 correction_note VARCHAR(1000) NOT NULL,created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 PRIMARY KEY(qualification_id),UNIQUE KEY uk_portal_correction_predecessor(tenant_id,predecessor_id),
 KEY idx_portal_correction_root(tenant_id,supplier_id,root_id,round_no),
 CONSTRAINT fk_portal_correction_submission FOREIGN KEY(qualification_id) REFERENCES qua_portal_submission(qualification_id),
 CONSTRAINT fk_portal_correction_predecessor FOREIGN KEY(predecessor_id) REFERENCES qua_portal_submission(qualification_id),
 CONSTRAINT fk_portal_correction_root FOREIGN KEY(root_id) REFERENCES qua_portal_submission(qualification_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
