CREATE TABLE qua_admission_application (
 id BIGINT NOT NULL,tenant_id BIGINT NOT NULL,organization_id BIGINT NOT NULL,supplier_id BIGINT NOT NULL,application_no VARCHAR(64) NOT NULL,
 purchase_category VARCHAR(100) NOT NULL,admission_reason VARCHAR(500) NOT NULL,expected_annual_amount DECIMAL(18,2) NULL,currency CHAR(3) NULL,
 status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',workflow_instance_id VARCHAR(64) NULL,current_reviewer_id BIGINT NULL,submitted_at DATETIME(3) NULL,decided_at DATETIME(3) NULL,
 created_by BIGINT NOT NULL,updated_by BIGINT NOT NULL,version INT NOT NULL DEFAULT 0,created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id),UNIQUE KEY uk_qua_admission_no(tenant_id,application_no),KEY idx_qua_admission_supplier(tenant_id,supplier_id,status),KEY idx_qua_admission_org(tenant_id,organization_id,status),
 CONSTRAINT fk_qua_admission_tenant FOREIGN KEY(tenant_id) REFERENCES iam_tenant(id),CONSTRAINT fk_qua_admission_org FOREIGN KEY(organization_id) REFERENCES iam_organization(id),CONSTRAINT fk_qua_admission_supplier FOREIGN KEY(supplier_id) REFERENCES sup_supplier(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE qua_admission_material (
 id BIGINT NOT NULL,tenant_id BIGINT NOT NULL,application_id BIGINT NOT NULL,material_type VARCHAR(64) NOT NULL,material_name VARCHAR(100) NOT NULL,file_id BIGINT NULL,required_flag TINYINT NOT NULL DEFAULT 1,provided_flag TINYINT NOT NULL DEFAULT 0,remark VARCHAR(300) NULL,sort_order INT NOT NULL DEFAULT 0,
 PRIMARY KEY(id),KEY idx_qua_material_application(tenant_id,application_id,sort_order),CONSTRAINT fk_qua_material_tenant FOREIGN KEY(tenant_id) REFERENCES iam_tenant(id),CONSTRAINT fk_qua_material_application FOREIGN KEY(application_id) REFERENCES qua_admission_application(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE qua_admission_review (
 id BIGINT NOT NULL,tenant_id BIGINT NOT NULL,application_id BIGINT NOT NULL,action VARCHAR(32) NOT NULL,from_status VARCHAR(32) NOT NULL,to_status VARCHAR(32) NOT NULL,comment_text VARCHAR(1000) NULL,operator_id BIGINT NOT NULL,operated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id),KEY idx_qua_review_application(tenant_id,application_id,operated_at),CONSTRAINT fk_qua_review_tenant FOREIGN KEY(tenant_id) REFERENCES iam_tenant(id),CONSTRAINT fk_qua_review_application FOREIGN KEY(application_id) REFERENCES qua_admission_application(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO sys_permission(id,permission_code,permission_name,permission_type,status) VALUES
 (6101,'supplier:admission:view','查看供应商准入','ACTION','ACTIVE'),
 (6102,'supplier:admission:apply','发起和补正供应商准入','ACTION','ACTIVE'),
 (6103,'supplier:admission:review','审核供应商准入','ACTION','ACTIVE');

INSERT INTO sys_menu(id,parent_id,module_code,menu_code,menu_name,route_path,component_key,icon,sort_order,status) VALUES
 (6101,6000,'SUPPLIER','SUPPLIER_ADMISSION','供应商准入','/suppliers/admissions','SupplierAdmissionPage','circle-check',20,'ACTIVE');
