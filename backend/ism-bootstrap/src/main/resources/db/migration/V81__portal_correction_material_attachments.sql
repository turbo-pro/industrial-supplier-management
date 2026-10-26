ALTER TABLE qua_portal_submission ADD COLUMN correction_attachment_required JSON NULL;
ALTER TABLE qua_portal_correction ADD COLUMN source_attachment_required JSON NULL;
CREATE TABLE qua_portal_upload_use (
 upload_id BIGINT NOT NULL,tenant_id BIGINT NOT NULL,supplier_id BIGINT NOT NULL,qualification_id BIGINT NOT NULL,
 usage_kind VARCHAR(20) NOT NULL,PRIMARY KEY(upload_id),KEY idx_portal_use_qualification(tenant_id,qualification_id),
 CONSTRAINT fk_portal_use_upload FOREIGN KEY(upload_id) REFERENCES res_portal_upload(upload_id),
 CONSTRAINT fk_portal_use_submission FOREIGN KEY(qualification_id) REFERENCES qua_portal_submission(qualification_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
INSERT INTO qua_portal_upload_use(upload_id,tenant_id,supplier_id,qualification_id,usage_kind)
 SELECT upload_id,tenant_id,supplier_id,qualification_id,'CERTIFICATE' FROM qua_portal_submission;
CREATE TABLE qua_correction_attachment (
 id BIGINT NOT NULL,tenant_id BIGINT NOT NULL,supplier_id BIGINT NOT NULL,qualification_id BIGINT NOT NULL,
 requirement_index INT NOT NULL,upload_id BIGINT NOT NULL,file_id BIGINT NOT NULL,file_name VARCHAR(255) NOT NULL,
 PRIMARY KEY(id),UNIQUE KEY uk_correction_attachment_upload(upload_id),KEY idx_correction_attachments(tenant_id,qualification_id,requirement_index,id),
 CONSTRAINT fk_correction_attachment_use FOREIGN KEY(upload_id) REFERENCES qua_portal_upload_use(upload_id),
 CONSTRAINT fk_correction_attachment_source FOREIGN KEY(qualification_id) REFERENCES qua_portal_correction(qualification_id),
 CONSTRAINT fk_correction_attachment_file FOREIGN KEY(file_id) REFERENCES res_file_object(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
