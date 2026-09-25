CREATE TABLE sup_blacklist_case (
 id BIGINT NOT NULL, tenant_id BIGINT NOT NULL, supplier_id BIGINT NOT NULL,
 organization_id BIGINT NOT NULL, supplier_code VARCHAR(64) NOT NULL, supplier_name VARCHAR(200) NOT NULL,
 reason VARCHAR(1000) NOT NULL, source_ref VARCHAR(200) NULL, status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
 review_comment VARCHAR(1000) NULL, reviewed_by BIGINT NULL, reviewed_at DATETIME(3) NULL,
 revoked_reason VARCHAR(1000) NULL, revoked_by BIGINT NULL, revoked_at DATETIME(3) NULL,
 created_by BIGINT NOT NULL, updated_by BIGINT NOT NULL, version INT NOT NULL DEFAULT 0,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id), KEY idx_sup_blacklist_supplier(tenant_id,supplier_id,status),
 CONSTRAINT fk_sup_blacklist_supplier FOREIGN KEY(supplier_id) REFERENCES sup_supplier(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sup_blacklist_event (
 id BIGINT NOT NULL, tenant_id BIGINT NOT NULL, case_id BIGINT NOT NULL,
 action VARCHAR(24) NOT NULL, from_status VARCHAR(24) NULL, to_status VARCHAR(24) NOT NULL,
 comment VARCHAR(1000) NULL, actor_id BIGINT NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id), KEY idx_sup_blacklist_event(tenant_id,case_id,created_at),
 CONSTRAINT fk_sup_blacklist_event_case FOREIGN KEY(case_id) REFERENCES sup_blacklist_case(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO sys_permission(id,permission_code,permission_name,permission_type,status) VALUES
 (6801,'supplier:blacklist:view','查看供应商黑名单','ACTION','ACTIVE'),
 (6802,'supplier:blacklist:manage','发起供应商黑名单','ACTION','ACTIVE'),
 (6803,'supplier:blacklist:review','审核和撤销供应商黑名单','ACTION','ACTIVE');
INSERT INTO sys_menu(id,parent_id,module_code,menu_code,menu_name,route_path,component_key,icon,sort_order,status) VALUES
 (6801,6000,'SUPPLIER','SUPPLIER_BLACKLIST','黑名单管理','/suppliers/blacklist','SupplierBlacklistPage','warning',50,'ACTIVE');
