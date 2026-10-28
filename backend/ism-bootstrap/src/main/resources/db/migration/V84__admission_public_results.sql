ALTER TABLE qua_admission_review ADD COLUMN application_version INT NULL;
-- Explicit, immutable publication only. Never backfill private historical review comments.
CREATE TABLE qua_admission_public_result (
 id BIGINT NOT NULL,tenant_id BIGINT NOT NULL,supplier_id BIGINT NOT NULL,application_id BIGINT NOT NULL,
 review_id BIGINT NOT NULL,source_version INT NOT NULL,application_no VARCHAR(64) NOT NULL,purchase_category VARCHAR(100) NOT NULL,
 result VARCHAR(32) NOT NULL,public_comment VARCHAR(1000) NOT NULL,published_by BIGINT NOT NULL,
 published_at DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)),
 PRIMARY KEY(id),UNIQUE KEY uk_admission_public_version(tenant_id,application_id,source_version),
 UNIQUE KEY uk_admission_public_review(review_id),KEY idx_admission_public_supplier(tenant_id,supplier_id,published_at,source_version,id),
 CONSTRAINT fk_admission_public_application FOREIGN KEY(application_id) REFERENCES qua_admission_application(id),
 CONSTRAINT fk_admission_public_review FOREIGN KEY(review_id) REFERENCES qua_admission_review(id),
 CONSTRAINT fk_admission_public_supplier FOREIGN KEY(supplier_id) REFERENCES sup_supplier(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_admission_portal_created ON qua_admission_portal_request(tenant_id,supplier_id,created_at,id);
