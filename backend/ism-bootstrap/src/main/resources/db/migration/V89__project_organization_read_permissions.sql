-- Organization-wide summary and detail are separate from site-local project actions.
INSERT INTO sys_permission(id,permission_code,permission_name,permission_type,status,module_code) VALUES
 (6314,'project:overview:view','查看组织项目总览','ACTION','ACTIVE','PROJECT'),
 (6315,'project:overview:detail','查看组织项目详情','ACTION','ACTIVE','PROJECT');
INSERT INTO iam_role_permission(tenant_id,role_id,permission_id)
SELECT r.tenant_id,r.id,p.id FROM iam_role r CROSS JOIN sys_permission p
WHERE r.role_code='TENANT_ADMIN' AND r.built_in=1 AND p.permission_code IN ('project:overview:view','project:overview:detail');
