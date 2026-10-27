CREATE TABLE qua_admission_portal_request (
 id BIGINT NOT NULL,tenant_id BIGINT NOT NULL,supplier_id BIGINT NOT NULL,application_id BIGINT NOT NULL,
 application_version INT NOT NULL,application_no VARCHAR(100) NOT NULL,instruction VARCHAR(1000) NOT NULL,
 state VARCHAR(20) NOT NULL DEFAULT 'OPEN',version INT NOT NULL DEFAULT 0,published_by BIGINT NOT NULL,
 submitted_by BIGINT NULL,submission_json JSON NULL,close_note VARCHAR(500) NULL,
 created_at DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)),submitted_at DATETIME(3) NULL,
 active_application_id BIGINT GENERATED ALWAYS AS(CASE WHEN state IN ('OPEN','SUBMITTED') THEN application_id ELSE NULL END) STORED,
 PRIMARY KEY(id),UNIQUE KEY uk_admission_portal_active(tenant_id,active_application_id),KEY idx_admission_portal_supplier(tenant_id,supplier_id,id),
 CONSTRAINT fk_admission_portal_application FOREIGN KEY(application_id) REFERENCES qua_admission_application(id),
 CONSTRAINT fk_admission_portal_supplier FOREIGN KEY(supplier_id) REFERENCES sup_supplier(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
ALTER TABLE qua_portal_upload_use MODIFY COLUMN qualification_id BIGINT NULL,
 ADD COLUMN admission_request_id BIGINT NULL,
 ADD CONSTRAINT fk_portal_use_admission FOREIGN KEY(admission_request_id) REFERENCES qua_admission_portal_request(id),
 ADD CONSTRAINT ck_portal_use_business CHECK(
  (qualification_id IS NOT NULL AND admission_request_id IS NULL AND usage_kind IN ('CERTIFICATE','MATERIAL')) OR
  (qualification_id IS NULL AND admission_request_id IS NOT NULL AND usage_kind='ADMISSION'));
CREATE TABLE qua_admission_portal_item (
 tenant_id BIGINT NOT NULL,request_id BIGINT NOT NULL,material_type VARCHAR(64) NOT NULL,material_name VARCHAR(100) NOT NULL,
 upload_id BIGINT NULL,file_id BIGINT NULL,file_name VARCHAR(255) NULL,sort_order INT NOT NULL,
 PRIMARY KEY(request_id,material_type),UNIQUE KEY uk_admission_portal_upload(upload_id),
 CONSTRAINT fk_admission_portal_item FOREIGN KEY(request_id) REFERENCES qua_admission_portal_request(id),
 CONSTRAINT fk_admission_portal_use FOREIGN KEY(upload_id) REFERENCES qua_portal_upload_use(upload_id),
 CONSTRAINT fk_admission_portal_file FOREIGN KEY(file_id) REFERENCES res_file_object(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
