CREATE TABLE res_portal_upload (
 upload_id BIGINT NOT NULL,tenant_id BIGINT NOT NULL,supplier_id BIGINT NOT NULL,account_id BIGINT NOT NULL,
 request_key CHAR(36) COLLATE ascii_bin NOT NULL,created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 PRIMARY KEY(upload_id),UNIQUE KEY uk_portal_upload_request(tenant_id,account_id,request_key),
 KEY idx_portal_upload_budget(tenant_id,supplier_id,created_at),
 CONSTRAINT fk_portal_upload_session FOREIGN KEY(upload_id) REFERENCES res_upload_session(id),
 CONSTRAINT fk_portal_upload_supplier FOREIGN KEY(supplier_id) REFERENCES sup_supplier(id),
 CONSTRAINT fk_portal_upload_account FOREIGN KEY(account_id) REFERENCES iam_portal_account(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE qua_portal_submission (
 qualification_id BIGINT NOT NULL,tenant_id BIGINT NOT NULL,supplier_id BIGINT NOT NULL,account_id BIGINT NOT NULL,
 request_key CHAR(36) COLLATE ascii_bin NOT NULL,upload_id BIGINT NOT NULL,
 request_json JSON NOT NULL,file_name VARCHAR(255) NOT NULL,created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 PRIMARY KEY(qualification_id),UNIQUE KEY uk_portal_certificate_request(tenant_id,supplier_id,request_key),
 UNIQUE KEY uk_portal_certificate_upload(upload_id),KEY idx_portal_certificate_history(tenant_id,supplier_id,created_at),
 CONSTRAINT fk_portal_certificate FOREIGN KEY(qualification_id) REFERENCES qua_supplier_qualification(id),
 CONSTRAINT fk_portal_certificate_upload FOREIGN KEY(upload_id) REFERENCES res_portal_upload(upload_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Preserve pending/valid uniqueness, but allow repeated rejection/renewal history for one certificate.
ALTER TABLE qua_supplier_qualification
 ADD COLUMN live_unique_status VARCHAR(24) GENERATED ALWAYS AS
  (CASE WHEN status IN ('DRAFT','VALID') THEN status ELSE NULL END) STORED,
 DROP INDEX uk_qua_certificate,
 ADD UNIQUE KEY uk_qua_certificate_live(tenant_id,qualification_type_id,certificate_no,live_unique_status);
