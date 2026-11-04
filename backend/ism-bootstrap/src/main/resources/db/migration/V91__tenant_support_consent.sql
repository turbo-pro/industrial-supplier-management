CREATE TABLE plt_tenant_support_policy (
 tenant_id BIGINT NOT NULL PRIMARY KEY,enabled TINYINT NOT NULL,role_id BIGINT NULL,
 allowed_fields_json JSON NOT NULL,max_minutes INT NOT NULL,version INT NOT NULL,
 updated_by BIGINT NOT NULL,updated_at DATETIME(3) NOT NULL,
 CONSTRAINT fk_support_policy_tenant FOREIGN KEY(tenant_id) REFERENCES plt_tenant(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE plt_support_tenant_decision (
 id BIGINT NOT NULL PRIMARY KEY,request_id BIGINT NOT NULL,tenant_id BIGINT NOT NULL,
 policy_version INT NOT NULL,request_version INT NOT NULL,status VARCHAR(16) NOT NULL,
 role_id BIGINT NULL,scope_json JSON NULL,expires_at DATETIME(3) NULL,
 reviewer_user_id BIGINT NOT NULL,reviewer_name VARCHAR(100) NOT NULL,comment VARCHAR(1000) NOT NULL,
 decided_at DATETIME(3) NOT NULL,version INT NOT NULL DEFAULT 0,
 revoked_by BIGINT NULL,revoke_reason VARCHAR(1000) NULL,revoked_at DATETIME(3) NULL,
 UNIQUE KEY uk_support_tenant_decision(request_id,policy_version),
 KEY idx_support_tenant_decision_tenant(tenant_id,request_id),
 CONSTRAINT fk_support_decision_request FOREIGN KEY(request_id) REFERENCES plt_support_request(id),
 CONSTRAINT fk_support_decision_tenant FOREIGN KEY(tenant_id) REFERENCES plt_tenant(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
INSERT INTO sys_permission(id,permission_code,permission_name,permission_type,status,module_code) VALUES
 (3080,'iam:support:view','查看本租户支持授权','ACTION','ACTIVE','SYSTEM'),
 (3081,'iam:support:manage','管理本租户支持策略','ACTION','ACTIVE','SYSTEM'),
 (3082,'iam:support:review','审批及撤回本租户支持','ACTION','ACTIVE','SYSTEM');
INSERT INTO sys_menu(id,parent_id,module_code,menu_code,menu_name,route_path,component_key,icon,sort_order,status)
 VALUES(3131,3100,'SYSTEM','TENANT_SUPPORT','支持授权','/system/support-authorization','TenantSupportPage','setting',65,'ACTIVE');
INSERT INTO iam_role_permission(tenant_id,role_id,permission_id)
 SELECT r.tenant_id,r.id,p.id FROM iam_role r JOIN sys_permission p ON p.id IN(3080,3081,3082) WHERE r.role_code='TENANT_ADMIN' AND r.built_in=1;
INSERT INTO iam_role_menu(tenant_id,role_id,menu_id)
 SELECT tenant_id,id,3131 FROM iam_role WHERE role_code='TENANT_ADMIN' AND built_in=1;
