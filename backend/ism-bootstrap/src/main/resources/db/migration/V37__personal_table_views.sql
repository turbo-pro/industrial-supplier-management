CREATE TABLE ui_table_view_owner (
 tenant_id BIGINT NOT NULL,owner_id BIGINT NOT NULL,table_key VARCHAR(80) NOT NULL,
 PRIMARY KEY(tenant_id,owner_id,table_key),
 CONSTRAINT fk_table_owner_tenant FOREIGN KEY(tenant_id) REFERENCES iam_tenant(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ui_table_view (
 id BIGINT NOT NULL,tenant_id BIGINT NOT NULL,owner_id BIGINT NOT NULL,table_key VARCHAR(80) NOT NULL,
 view_name VARCHAR(60) NOT NULL,columns_json JSON NOT NULL,is_default TINYINT NOT NULL DEFAULT 0,
 default_owner BIGINT GENERATED ALWAYS AS (CASE WHEN is_default=1 THEN owner_id ELSE NULL END) STORED,
 version INT NOT NULL DEFAULT 0,updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id),UNIQUE KEY uk_table_view_name(tenant_id,owner_id,table_key,view_name),
 UNIQUE KEY uk_table_view_default(tenant_id,table_key,default_owner),
 CONSTRAINT fk_table_view_owner FOREIGN KEY(tenant_id,owner_id,table_key) REFERENCES ui_table_view_owner(tenant_id,owner_id,table_key),
 CONSTRAINT ck_table_view_default CHECK(is_default IN (0,1)),CONSTRAINT ck_table_view_version CHECK(version>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO sys_permission(id,permission_code,permission_name,permission_type,status)
VALUES (7401,'table:view:manage','管理个人表格列方案','ACTION','ACTIVE');
