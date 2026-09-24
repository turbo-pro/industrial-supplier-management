CREATE TABLE sup_supplier (
 id BIGINT NOT NULL,tenant_id BIGINT NOT NULL,organization_id BIGINT NOT NULL,supplier_code VARCHAR(64) NOT NULL,supplier_name VARCHAR(200) NOT NULL,short_name VARCHAR(100) NULL,
 unified_social_credit_code VARCHAR(32) NULL,supplier_type VARCHAR(32) NOT NULL,industry VARCHAR(100) NULL,country_code CHAR(2) NOT NULL DEFAULT 'CN',province VARCHAR(100) NULL,city VARCHAR(100) NULL,address VARCHAR(300) NULL,
 legal_representative VARCHAR(100) NULL,registered_capital DECIMAL(18,2) NULL,currency CHAR(3) NULL,established_date DATE NULL,website VARCHAR(300) NULL,source VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
 status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',risk_level VARCHAR(16) NOT NULL DEFAULT 'LOW',remark VARCHAR(1000) NULL,created_by BIGINT NOT NULL,updated_by BIGINT NOT NULL,version INT NOT NULL DEFAULT 0,deleted TINYINT NOT NULL DEFAULT 0,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id),UNIQUE KEY uk_sup_supplier_code(tenant_id,supplier_code,deleted),UNIQUE KEY uk_sup_supplier_credit(tenant_id,unified_social_credit_code,deleted),
 KEY idx_sup_supplier_name(tenant_id,supplier_name),KEY idx_sup_supplier_org_status(tenant_id,organization_id,status),
 CONSTRAINT fk_sup_supplier_tenant FOREIGN KEY(tenant_id) REFERENCES iam_tenant(id),CONSTRAINT fk_sup_supplier_org FOREIGN KEY(organization_id) REFERENCES iam_organization(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sup_supplier_contact (
 id BIGINT NOT NULL,tenant_id BIGINT NOT NULL,supplier_id BIGINT NOT NULL,contact_name VARCHAR(100) NOT NULL,position_name VARCHAR(100) NULL,mobile VARCHAR(32) NULL,telephone VARCHAR(32) NULL,email VARCHAR(200) NULL,is_primary TINYINT NOT NULL DEFAULT 0,sort_order INT NOT NULL DEFAULT 0,
 PRIMARY KEY(id),KEY idx_sup_contact_supplier(tenant_id,supplier_id,sort_order),CONSTRAINT fk_sup_contact_tenant FOREIGN KEY(tenant_id) REFERENCES iam_tenant(id),CONSTRAINT fk_sup_contact_supplier FOREIGN KEY(supplier_id) REFERENCES sup_supplier(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO sys_permission(id,permission_code,permission_name,permission_type,status) VALUES
 (6001,'supplier:master:view','查看供应商档案','ACTION','ACTIVE'),
 (6002,'supplier:master:create','创建供应商档案','ACTION','ACTIVE'),
 (6003,'supplier:master:update','修改供应商档案','ACTION','ACTIVE'),
 (6004,'supplier:master:status','变更供应商状态','ACTION','ACTIVE');

INSERT INTO sys_menu(id,parent_id,module_code,menu_code,menu_name,route_path,component_key,icon,sort_order,status) VALUES
 (6000,NULL,'SUPPLIER','SUPPLIER_MANAGEMENT','供应商管理','/suppliers','Layout','office-building',100,'ACTIVE'),
 (6001,6000,'SUPPLIER','SUPPLIER_MASTER','供应商档案','/suppliers/master','SupplierMasterPage','document',10,'ACTIVE');
