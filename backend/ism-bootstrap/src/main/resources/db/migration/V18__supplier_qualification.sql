CREATE TABLE qua_qualification_type (
 id BIGINT NOT NULL,tenant_id BIGINT NOT NULL,type_code VARCHAR(64) NOT NULL,type_name VARCHAR(100) NOT NULL,category VARCHAR(64) NOT NULL,
 validity_required TINYINT NOT NULL DEFAULT 1,default_warning_days INT NOT NULL DEFAULT 30,description VARCHAR(500) NULL,status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
 created_by BIGINT NOT NULL,updated_by BIGINT NOT NULL,version INT NOT NULL DEFAULT 0,created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id),UNIQUE KEY uk_qua_type_code(tenant_id,type_code),KEY idx_qua_type_status(tenant_id,status),CONSTRAINT fk_qua_type_tenant FOREIGN KEY(tenant_id) REFERENCES iam_tenant(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE qua_supplier_qualification (
 id BIGINT NOT NULL,tenant_id BIGINT NOT NULL,organization_id BIGINT NOT NULL,supplier_id BIGINT NOT NULL,qualification_type_id BIGINT NOT NULL,parent_qualification_id BIGINT NULL,
 certificate_no VARCHAR(100) NOT NULL,issuing_authority VARCHAR(200) NULL,issue_date DATE NULL,effective_date DATE NULL,expiry_date DATE NULL,permanent_flag TINYINT NOT NULL DEFAULT 0,
 warning_days INT NOT NULL DEFAULT 30,file_id BIGINT NOT NULL,status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',verification_comment VARCHAR(1000) NULL,verified_by BIGINT NULL,verified_at DATETIME(3) NULL,
 revoked_reason VARCHAR(500) NULL,created_by BIGINT NOT NULL,updated_by BIGINT NOT NULL,version INT NOT NULL DEFAULT 0,created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id),UNIQUE KEY uk_qua_certificate(tenant_id,qualification_type_id,certificate_no,status),KEY idx_qua_supplier_expiry(tenant_id,supplier_id,expiry_date,status),KEY idx_qua_org_status(tenant_id,organization_id,status),
 CONSTRAINT fk_qua_supplier_qualification_tenant FOREIGN KEY(tenant_id) REFERENCES iam_tenant(id),CONSTRAINT fk_qua_supplier_qualification_org FOREIGN KEY(organization_id) REFERENCES iam_organization(id),
 CONSTRAINT fk_qua_supplier_qualification_supplier FOREIGN KEY(supplier_id) REFERENCES sup_supplier(id),CONSTRAINT fk_qua_supplier_qualification_type FOREIGN KEY(qualification_type_id) REFERENCES qua_qualification_type(id),CONSTRAINT fk_qua_supplier_qualification_parent FOREIGN KEY(parent_qualification_id) REFERENCES qua_supplier_qualification(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO sys_permission(id,permission_code,permission_name,permission_type,status) VALUES
 (6201,'supplier:qualification:view','查看供应商资质','ACTION','ACTIVE'),
 (6202,'supplier:qualification:manage','维护供应商资质','ACTION','ACTIVE'),
 (6203,'supplier:qualification:verify','核验供应商资质','ACTION','ACTIVE'),
 (6204,'supplier:qualification:type','配置资质类型','ACTION','ACTIVE');

INSERT INTO sys_menu(id,parent_id,module_code,menu_code,menu_name,route_path,component_key,icon,sort_order,status) VALUES
 (6201,6000,'QUALIFICATION','SUPPLIER_QUALIFICATION','资质证照','/suppliers/qualifications','SupplierQualificationPage','postcard',30,'ACTIVE');
