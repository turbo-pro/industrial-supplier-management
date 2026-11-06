INSERT INTO plt_permission(id,permission_code,permission_name,status) VALUES
 (1128,'platform:support:terminate','强制终止支持会话','ACTIVE');
INSERT INTO plt_role_permission(role_id,permission_id) VALUES(1001,1128);
