CREATE TABLE sup_purchase_category (
 id BIGINT NOT NULL,tenant_id BIGINT NOT NULL,category_code VARCHAR(64) NOT NULL,category_name VARCHAR(100) NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',version INT NOT NULL DEFAULT 0,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id),UNIQUE KEY uk_sup_purchase_category_code(tenant_id,category_code),KEY idx_sup_purchase_category_status(tenant_id,status),
 CONSTRAINT fk_sup_purchase_category_tenant FOREIGN KEY(tenant_id) REFERENCES iam_tenant(id),
 CONSTRAINT chk_sup_purchase_category_status CHECK(status IN ('ACTIVE','INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sup_supplier_purchase_category (
 tenant_id BIGINT NOT NULL,supplier_id BIGINT NOT NULL,category_id BIGINT NOT NULL,
 PRIMARY KEY(tenant_id,supplier_id,category_id),KEY idx_sup_supplier_category_category(tenant_id,category_id,supplier_id),
 CONSTRAINT fk_sup_supplier_category_supplier FOREIGN KEY(supplier_id) REFERENCES sup_supplier(id),
 CONSTRAINT fk_sup_supplier_category_category FOREIGN KEY(category_id) REFERENCES sup_purchase_category(id),
 CONSTRAINT fk_sup_supplier_category_tenant FOREIGN KEY(tenant_id) REFERENCES iam_tenant(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO sys_permission(id,permission_code,permission_name,permission_type,status)
VALUES(6070,'supplier:category:manage','维护租户采购品类','ACTION','ACTIVE');

INSERT INTO iam_role_permission(tenant_id,role_id,permission_id)
SELECT tenant_id,id,6070 FROM iam_role WHERE role_code='TENANT_ADMIN' AND status='ACTIVE';
