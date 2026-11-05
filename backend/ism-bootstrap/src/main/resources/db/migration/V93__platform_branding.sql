CREATE TABLE plt_branding (
 id INT NOT NULL PRIMARY KEY,system_name VARCHAR(100) NOT NULL,logo_url VARCHAR(2000) NOT NULL,
 favicon_url VARCHAR(2000) NOT NULL,footer_text VARCHAR(300) NOT NULL,version INT NOT NULL DEFAULT 0,
 updated_by BIGINT NULL,updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
INSERT INTO plt_branding(id,system_name,logo_url,favicon_url,footer_text) VALUES(1,'工业供应商管理系统 Console','','','');
INSERT INTO plt_permission(id,permission_code,permission_name,status) VALUES
 (1126,'platform:branding:view','查看平台品牌配置','ACTIVE'),
 (1127,'platform:branding:manage','管理平台品牌配置','ACTIVE');
INSERT INTO plt_role_permission(role_id,permission_id) VALUES(1001,1126),(1001,1127);
