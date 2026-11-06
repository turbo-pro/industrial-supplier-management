INSERT INTO plt_permission(id,permission_code,permission_name,status) VALUES
 (1129,'platform:support:audit','审计支持会话','ACTIVE');
INSERT INTO plt_role(id,role_code,role_name,status) VALUES
 (1003,'PLATFORM_SUPPORT_AUDITOR','支持会话审计员','ACTIVE');
INSERT INTO plt_role_permission(role_id,permission_id) VALUES(1001,1129),(1003,1129);
