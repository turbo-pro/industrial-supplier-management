CREATE TABLE prj_contract (
 id BIGINT NOT NULL,tenant_id BIGINT NOT NULL,organization_id BIGINT NOT NULL,supplier_id BIGINT NOT NULL,contract_no VARCHAR(64) NOT NULL,contract_name VARCHAR(200) NOT NULL,contract_type VARCHAR(32) NOT NULL,
 amount DECIMAL(18,2) NOT NULL,currency CHAR(3) NOT NULL DEFAULT 'CNY',signed_date DATE NULL,start_date DATE NOT NULL,end_date DATE NOT NULL,owner_id BIGINT NOT NULL,file_id BIGINT NOT NULL,
 status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',termination_reason VARCHAR(500) NULL,created_by BIGINT NOT NULL,updated_by BIGINT NOT NULL,version INT NOT NULL DEFAULT 0,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id),UNIQUE KEY uk_prj_contract_no(tenant_id,contract_no),KEY idx_prj_contract_supplier(tenant_id,supplier_id,status),KEY idx_prj_contract_org(tenant_id,organization_id,status),
 CONSTRAINT fk_prj_contract_tenant FOREIGN KEY(tenant_id) REFERENCES iam_tenant(id),CONSTRAINT fk_prj_contract_org FOREIGN KEY(organization_id) REFERENCES iam_organization(id),CONSTRAINT fk_prj_contract_supplier FOREIGN KEY(supplier_id) REFERENCES sup_supplier(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE prj_project (
 id BIGINT NOT NULL,tenant_id BIGINT NOT NULL,organization_id BIGINT NOT NULL,supplier_id BIGINT NOT NULL,contract_id BIGINT NULL,project_code VARCHAR(64) NOT NULL,project_name VARCHAR(200) NOT NULL,project_type VARCHAR(32) NOT NULL,
 site_address VARCHAR(300) NULL,planned_start_date DATE NOT NULL,planned_end_date DATE NOT NULL,actual_start_date DATE NULL,actual_end_date DATE NULL,manager_id BIGINT NOT NULL,budget_amount DECIMAL(18,2) NULL,currency CHAR(3) NULL,
 status VARCHAR(24) NOT NULL DEFAULT 'PLANNED',status_reason VARCHAR(500) NULL,created_by BIGINT NOT NULL,updated_by BIGINT NOT NULL,version INT NOT NULL DEFAULT 0,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id),UNIQUE KEY uk_prj_project_code(tenant_id,project_code),KEY idx_prj_project_supplier(tenant_id,supplier_id,status),KEY idx_prj_project_org(tenant_id,organization_id,status),KEY idx_prj_project_contract(tenant_id,contract_id),
 CONSTRAINT fk_prj_project_tenant FOREIGN KEY(tenant_id) REFERENCES iam_tenant(id),CONSTRAINT fk_prj_project_org FOREIGN KEY(organization_id) REFERENCES iam_organization(id),CONSTRAINT fk_prj_project_supplier FOREIGN KEY(supplier_id) REFERENCES sup_supplier(id),CONSTRAINT fk_prj_project_contract FOREIGN KEY(contract_id) REFERENCES prj_contract(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO sys_permission(id,permission_code,permission_name,permission_type,status) VALUES
 (6301,'contract:view','查看合同台账','ACTION','ACTIVE'),(6302,'contract:manage','维护合同','ACTION','ACTIVE'),(6303,'contract:status','变更合同状态','ACTION','ACTIVE'),
 (6311,'project:view','查看项目台账','ACTION','ACTIVE'),(6312,'project:manage','维护项目','ACTION','ACTIVE'),(6313,'project:status','变更项目状态','ACTION','ACTIVE');

INSERT INTO sys_menu(id,parent_id,module_code,menu_code,menu_name,route_path,component_key,icon,sort_order,status) VALUES
 (6300,NULL,'PROJECT','CONTRACT_PROJECT','合同与项目','/projects','Layout','briefcase',200,'ACTIVE'),
 (6301,6300,'PROJECT','CONTRACT_LEDGER','合同台账','/projects/contracts','ContractPage','tickets',10,'ACTIVE'),
 (6311,6300,'PROJECT','PROJECT_LEDGER','项目台账','/projects/ledger','ProjectPage','management',20,'ACTIVE');
