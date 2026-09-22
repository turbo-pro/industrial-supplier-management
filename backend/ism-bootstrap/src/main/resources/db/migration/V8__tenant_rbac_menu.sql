CREATE TABLE sys_permission (
    id BIGINT NOT NULL,
    permission_code VARCHAR(100) NOT NULL,
    permission_name VARCHAR(100) NOT NULL,
    permission_type VARCHAR(20) NOT NULL,
    status VARCHAR(32) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_permission_code (permission_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_menu (
    id BIGINT NOT NULL,
    parent_id BIGINT NULL,
    module_code VARCHAR(64) NOT NULL,
    menu_code VARCHAR(64) NOT NULL,
    menu_name VARCHAR(100) NOT NULL,
    route_path VARCHAR(200) NOT NULL,
    component_key VARCHAR(100) NOT NULL,
    icon VARCHAR(64) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_menu_code (menu_code),
    CONSTRAINT fk_sys_menu_parent FOREIGN KEY (parent_id) REFERENCES sys_menu(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_role (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    role_name VARCHAR(100) NOT NULL,
    built_in TINYINT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_iam_role_code (tenant_id,role_code),
    CONSTRAINT fk_iam_role_tenant FOREIGN KEY (tenant_id) REFERENCES iam_tenant(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_user_role (
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (tenant_id,user_id,role_id),
    KEY idx_iam_user_role_role (tenant_id,role_id,user_id),
    CONSTRAINT fk_iam_user_role_tenant FOREIGN KEY (tenant_id) REFERENCES iam_tenant(id),
    CONSTRAINT fk_iam_user_role_user FOREIGN KEY (user_id) REFERENCES iam_user(id),
    CONSTRAINT fk_iam_user_role_role FOREIGN KEY (role_id) REFERENCES iam_role(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_role_permission (
    tenant_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (tenant_id,role_id,permission_id),
    CONSTRAINT fk_iam_role_permission_tenant FOREIGN KEY (tenant_id) REFERENCES iam_tenant(id),
    CONSTRAINT fk_iam_role_permission_role FOREIGN KEY (role_id) REFERENCES iam_role(id),
    CONSTRAINT fk_iam_role_permission_permission FOREIGN KEY (permission_id) REFERENCES sys_permission(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_role_menu (
    tenant_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    PRIMARY KEY (tenant_id,role_id,menu_id),
    CONSTRAINT fk_iam_role_menu_tenant FOREIGN KEY (tenant_id) REFERENCES iam_tenant(id),
    CONSTRAINT fk_iam_role_menu_role FOREIGN KEY (role_id) REFERENCES iam_role(id),
    CONSTRAINT fk_iam_role_menu_menu FOREIGN KEY (menu_id) REFERENCES sys_menu(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_role_data_scope (
    tenant_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    resource_code VARCHAR(64) NOT NULL,
    scope_type VARCHAR(32) NOT NULL,
    PRIMARY KEY (tenant_id,role_id,resource_code),
    CONSTRAINT fk_iam_role_scope_tenant FOREIGN KEY (tenant_id) REFERENCES iam_tenant(id),
    CONSTRAINT fk_iam_role_scope_role FOREIGN KEY (role_id) REFERENCES iam_role(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_role_data_scope_organization (
    tenant_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    resource_code VARCHAR(64) NOT NULL,
    organization_id BIGINT NOT NULL,
    PRIMARY KEY (tenant_id,role_id,resource_code,organization_id),
    CONSTRAINT fk_iam_role_scope_org_tenant FOREIGN KEY (tenant_id) REFERENCES iam_tenant(id),
    CONSTRAINT fk_iam_role_scope_org_role FOREIGN KEY (role_id) REFERENCES iam_role(id),
    CONSTRAINT fk_iam_role_scope_org_organization FOREIGN KEY (organization_id) REFERENCES iam_organization(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO sys_permission(id,permission_code,permission_name,permission_type,status) VALUES
 (3001,'iam:organization:view','查看组织','ACTION','ACTIVE'),
 (3002,'iam:organization:manage','管理组织','ACTION','ACTIVE'),
 (3003,'iam:user:view','查看用户','ACTION','ACTIVE'),
 (3004,'iam:user:manage','管理用户','ACTION','ACTIVE'),
 (3005,'iam:role:view','查看角色','ACTION','ACTIVE'),
 (3006,'iam:role:manage','管理角色与授权','ACTION','ACTIVE'),
 (3007,'iam:menu:view','查看当前菜单','ACTION','ACTIVE');

INSERT INTO sys_menu(id,parent_id,module_code,menu_code,menu_name,route_path,component_key,icon,sort_order,status) VALUES
 (3100,NULL,'SYSTEM','SYSTEM_MANAGEMENT','系统管理','/system','Layout','setting',900,'ACTIVE'),
 (3101,3100,'SYSTEM','ORGANIZATION_MANAGEMENT','组织管理','/system/organizations','OrganizationPage','organization',10,'ACTIVE'),
 (3102,3100,'SYSTEM','USER_MANAGEMENT','用户管理','/system/users','UserPage','user',20,'ACTIVE'),
 (3103,3100,'SYSTEM','ROLE_MANAGEMENT','角色权限','/system/roles','RolePage','lock',30,'ACTIVE');
