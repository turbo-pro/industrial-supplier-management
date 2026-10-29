INSERT INTO plt_permission(id,permission_code,permission_name,status)
VALUES(1123,'platform:tenant:upgrade','升级多场站运营','ACTIVE');
INSERT INTO plt_role_permission(role_id,permission_id)
SELECT id,1123 FROM plt_role WHERE role_code='PLATFORM_ADMIN';
