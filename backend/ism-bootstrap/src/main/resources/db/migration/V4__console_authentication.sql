CREATE TABLE plt_user (
    id BIGINT NOT NULL,
    username VARCHAR(100) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    status VARCHAR(32) NOT NULL,
    force_password_change TINYINT NOT NULL DEFAULT 0,
    password_changed_at DATETIME(3) NULL,
    last_login_at DATETIME(3) NULL,
    locked_until DATETIME(3) NULL,
    failed_count INT NOT NULL DEFAULT 0,
    token_version INT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_plt_user_name (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE plt_role (
    id BIGINT NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    role_name VARCHAR(100) NOT NULL,
    status VARCHAR(32) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_plt_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE plt_permission (
    id BIGINT NOT NULL,
    permission_code VARCHAR(100) NOT NULL,
    permission_name VARCHAR(100) NOT NULL,
    status VARCHAR(32) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_plt_permission_code (permission_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE plt_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id,role_id),
    CONSTRAINT fk_plt_user_role_user FOREIGN KEY (user_id) REFERENCES plt_user(id),
    CONSTRAINT fk_plt_user_role_role FOREIGN KEY (role_id) REFERENCES plt_role(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE plt_role_permission (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id,permission_id),
    CONSTRAINT fk_plt_role_permission_role FOREIGN KEY (role_id) REFERENCES plt_role(id),
    CONSTRAINT fk_plt_role_permission_permission FOREIGN KEY (permission_id) REFERENCES plt_permission(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE plt_refresh_token (
    id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    token_hash CHAR(64) NOT NULL,
    family_id CHAR(36) NOT NULL,
    issued_at DATETIME(3) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    revoked_at DATETIME(3) NULL,
    revoke_reason VARCHAR(100) NULL,
    replaced_by_hash CHAR(64) NULL,
    device_id VARCHAR(128) NOT NULL,
    ip VARCHAR(64) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_plt_refresh_hash (token_hash),
    KEY idx_plt_refresh_family (family_id),
    KEY idx_plt_refresh_user (user_id),
    CONSTRAINT fk_plt_refresh_user FOREIGN KEY (user_id) REFERENCES plt_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO plt_role(id,role_code,role_name,status) VALUES
    (1001,'PLATFORM_ADMIN','平台运营管理员','ACTIVE'),
    (1002,'PLATFORM_SUPPORT','平台支持人员','ACTIVE');

INSERT INTO plt_permission(id,permission_code,permission_name,status) VALUES
    (1101,'platform:tenant:view','查看租户控制面摘要','ACTIVE'),
    (1102,'platform:tenant:create','创建租户','ACTIVE'),
    (1103,'platform:tenant:update','修改租户','ACTIVE'),
    (1104,'platform:tenant:initialize','初始化租户','ACTIVE'),
    (1105,'platform:tenant:suspend','暂停租户','ACTIVE'),
    (1106,'platform:tenant:resume','恢复租户','ACTIVE'),
    (1107,'platform:tenant:cancel','注销租户','ACTIVE'),
    (1110,'platform:package:view','查看套餐','ACTIVE'),
    (1111,'platform:package:manage','管理套餐','ACTIVE'),
    (1112,'platform:package:publish','发布套餐','ACTIVE'),
    (1113,'platform:package:assign','分配套餐','ACTIVE'),
    (1120,'platform:support:request','申请支持访问','ACTIVE');

INSERT INTO plt_role_permission(role_id,permission_id)
SELECT 1001,id FROM plt_permission;
INSERT INTO plt_role_permission(role_id,permission_id)
SELECT 1002,id FROM plt_permission WHERE permission_code IN ('platform:tenant:view','platform:package:view','platform:support:request');
