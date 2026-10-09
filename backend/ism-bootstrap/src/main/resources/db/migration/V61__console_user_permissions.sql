INSERT INTO plt_permission(id,permission_code,permission_name,status) VALUES
    (1121,'platform:user:view','查看平台账号','ACTIVE'),
    (1122,'platform:user:manage','管理平台账号','ACTIVE');

INSERT INTO plt_role_permission(role_id,permission_id)
SELECT 1001,id FROM plt_permission WHERE permission_code IN ('platform:user:view','platform:user:manage');
