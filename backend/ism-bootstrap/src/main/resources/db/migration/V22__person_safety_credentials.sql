ALTER TABLE res_supplier_person ADD COLUMN special_work_type VARCHAR(32) NULL AFTER trade_type;

CREATE TABLE saf_person_credential (
 id BIGINT NOT NULL, tenant_id BIGINT NOT NULL, organization_id BIGINT NOT NULL,
 supplier_id BIGINT NOT NULL, person_id BIGINT NOT NULL, project_id BIGINT NULL,
 credential_no VARCHAR(64) NOT NULL, credential_kind VARCHAR(24) NOT NULL,
 work_type VARCHAR(32) NULL, title VARCHAR(200) NOT NULL, exam_score DECIMAL(5,2) NULL,
 passed TINYINT(1) NULL,
 effective_date DATE NOT NULL, expiry_date DATE NOT NULL, file_id BIGINT NOT NULL,
 status VARCHAR(24) NOT NULL DEFAULT 'PENDING', review_comment VARCHAR(500) NULL,
 reviewed_by BIGINT NULL, reviewed_at DATETIME(3) NULL,
 created_by BIGINT NOT NULL, updated_by BIGINT NOT NULL, version INT NOT NULL DEFAULT 0,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id), UNIQUE KEY uk_saf_credential_no(tenant_id,credential_no),
 KEY idx_saf_credential_person(tenant_id,person_id,credential_kind,status,expiry_date),
 KEY idx_saf_credential_project(tenant_id,project_id),
 CONSTRAINT fk_saf_credential_tenant FOREIGN KEY(tenant_id) REFERENCES iam_tenant(id),
 CONSTRAINT fk_saf_credential_person FOREIGN KEY(person_id) REFERENCES res_supplier_person(id),
 CONSTRAINT fk_saf_credential_file FOREIGN KEY(file_id) REFERENCES res_file_object(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO sys_permission(id,permission_code,permission_name,permission_type,status) VALUES
 (6511,'safety:credential:view','查看人员安全凭证','ACTION','ACTIVE'),
 (6512,'safety:credential:create','登记人员安全凭证','ACTION','ACTIVE'),
 (6513,'safety:credential:review','审核人员安全凭证','ACTION','ACTIVE');
INSERT INTO sys_menu(id,parent_id,module_code,menu_code,menu_name,route_path,component_key,icon,sort_order,status) VALUES
 (6511,6500,'SAFETY','SAFETY_CREDENTIAL','培训与作业凭证','/safety/credentials','SafetyCredentialPage','document-checked',20,'ACTIVE');
