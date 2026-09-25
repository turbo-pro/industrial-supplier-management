CREATE TABLE qua_nonconformance (
 id BIGINT NOT NULL, tenant_id BIGINT NOT NULL, organization_id BIGINT NOT NULL,
 project_id BIGINT NOT NULL, supplier_id BIGINT NOT NULL,
 ncr_no VARCHAR(64) NOT NULL, title VARCHAR(200) NOT NULL,
 category VARCHAR(32) NOT NULL, severity VARCHAR(16) NOT NULL,
 description VARCHAR(2000) NOT NULL, inspection_date DATE NOT NULL,
 inspected_quantity DECIMAL(18,3) NOT NULL, defective_quantity DECIMAL(18,3) NOT NULL,
 unit VARCHAR(32) NOT NULL, evidence_file_id BIGINT NOT NULL,
 deadline DATE NOT NULL, responsible_user_id BIGINT NOT NULL,
 status VARCHAR(24) NOT NULL DEFAULT 'OPEN',
 root_cause VARCHAR(2000) NULL, correction VARCHAR(2000) NULL,
 preventive_action VARCHAR(2000) NULL, action_file_id BIGINT NULL,
 submitted_at DATETIME(3) NULL, verification_result VARCHAR(16) NULL,
 verification_comment VARCHAR(1000) NULL, verified_by BIGINT NULL,
 verified_at DATETIME(3) NULL,
 created_by BIGINT NOT NULL, updated_by BIGINT NOT NULL,
 version INT NOT NULL DEFAULT 0,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id), UNIQUE KEY uk_qua_ncr_no(tenant_id,ncr_no),
 KEY idx_qua_ncr_project(tenant_id,project_id,status),
 KEY idx_qua_ncr_supplier(tenant_id,supplier_id,status),
 KEY idx_qua_ncr_deadline(tenant_id,status,deadline),
 CONSTRAINT fk_qua_ncr_tenant FOREIGN KEY(tenant_id) REFERENCES iam_tenant(id),
 CONSTRAINT fk_qua_ncr_project FOREIGN KEY(project_id) REFERENCES prj_project(id),
 CONSTRAINT fk_qua_ncr_supplier FOREIGN KEY(supplier_id) REFERENCES sup_supplier(id),
 CONSTRAINT fk_qua_ncr_evidence FOREIGN KEY(evidence_file_id) REFERENCES res_file_object(id),
 CONSTRAINT fk_qua_ncr_action_file FOREIGN KEY(action_file_id) REFERENCES res_file_object(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE qua_ncr_event (
 id BIGINT NOT NULL, tenant_id BIGINT NOT NULL, ncr_id BIGINT NOT NULL,
 action VARCHAR(24) NOT NULL, from_status VARCHAR(24) NULL, to_status VARCHAR(24) NOT NULL,
 note TEXT NULL, file_id BIGINT NULL, actor_id BIGINT NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id), KEY idx_qua_ncr_event(tenant_id,ncr_id,created_at),
 CONSTRAINT fk_qua_event_ncr FOREIGN KEY(ncr_id) REFERENCES qua_nonconformance(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO sys_permission(id,permission_code,permission_name,permission_type,status) VALUES
 (6601,'quality:ncr:view','查看质量不符合项','ACTION','ACTIVE'),
 (6602,'quality:ncr:create','登记质量不符合项','ACTION','ACTIVE'),
 (6603,'quality:ncr:rectify','提交质量纠正措施','ACTION','ACTIVE'),
 (6604,'quality:ncr:verify','复验质量纠正措施','ACTION','ACTIVE');
INSERT INTO sys_menu(id,parent_id,module_code,menu_code,menu_name,route_path,component_key,icon,sort_order,status) VALUES
 (6600,NULL,'QUALITY','QUALITY_MANAGEMENT','质量管理','/quality','Layout','circle-check',500,'ACTIVE'),
 (6601,6600,'QUALITY','QUALITY_NCR','质量不符合项','/quality/nonconformances','QualityNonconformancePage','document-delete',10,'ACTIVE');
