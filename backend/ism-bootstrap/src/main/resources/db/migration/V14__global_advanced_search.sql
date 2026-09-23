CREATE TABLE src_saved_search (
 id BIGINT NOT NULL,tenant_id BIGINT NOT NULL,owner_id BIGINT NOT NULL,search_name VARCHAR(100) NOT NULL,
 query_json JSON NOT NULL,is_default TINYINT NOT NULL DEFAULT 0,version INT NOT NULL DEFAULT 0,
 default_owner_id BIGINT GENERATED ALWAYS AS (CASE WHEN is_default=1 THEN owner_id ELSE NULL END) STORED,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id),UNIQUE KEY uk_src_saved_search_name(tenant_id,owner_id,search_name),UNIQUE KEY uk_src_saved_search_default(tenant_id,default_owner_id),
 KEY idx_src_saved_search_owner(tenant_id,owner_id,is_default,updated_at),
 CONSTRAINT fk_src_saved_search_tenant FOREIGN KEY(tenant_id) REFERENCES iam_tenant(id),
 CONSTRAINT fk_src_saved_search_owner FOREIGN KEY(owner_id) REFERENCES iam_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO sys_permission(id,permission_code,permission_name,permission_type,status) VALUES
 (5401,'search:global:use','使用全局与高级搜索','ACTION','ACTIVE'),
 (5402,'search:saved:manage','管理个人搜索方案','ACTION','ACTIVE');

INSERT INTO sys_menu(id,parent_id,module_code,menu_code,menu_name,route_path,component_key,icon,sort_order,status) VALUES
 (5401,5000,'RESOURCE','ADVANCED_SEARCH','高级搜索','/resources/search','AdvancedSearchPage','search',40,'ACTIVE');
