-- Local profile only. Password for both demo users: Admin@123456
-- Never add classpath:db/local to a shared, demo or production deployment.
INSERT IGNORE INTO plt_user(id,username,display_name,password_hash,status,force_password_change,password_changed_at)
VALUES (9001,'platform-admin','本地平台管理员','$2y$10$P7I1Cpqar23RGcyOjR1MNOQ7vIhgIP0aJlQ3hygqasaKJjO/yWbyC','ACTIVE',0,CURRENT_TIMESTAMP(3));
INSERT IGNORE INTO plt_user_role(user_id,role_id) VALUES (9001,1001);

INSERT IGNORE INTO plt_tenant(id,tenant_code,tenant_name,status,initialization_status,initialized_at)
VALUES (9100,'demo','演示制造企业','ACTIVE','COMPLETED',CURRENT_TIMESTAMP(3));
INSERT IGNORE INTO iam_tenant(id,tenant_code,tenant_name,status,timezone,locale)
VALUES (9100,'demo','演示制造企业','ACTIVE','Asia/Shanghai','zh-CN');
INSERT IGNORE INTO iam_organization(id,tenant_id,parent_id,organization_code,organization_name,organization_type,status,sort_order)
VALUES (9101,9100,NULL,'HQ','演示制造企业总部','HEADQUARTERS','ACTIVE',0),
       (9102,9100,9101,'PLANT-01','一号生产基地','SITE','ACTIVE',10);
INSERT IGNORE INTO iam_organization_closure(tenant_id,ancestor_id,descendant_id,depth)
VALUES (9100,9101,9101,0),(9100,9101,9102,1),(9100,9102,9102,0);
INSERT IGNORE INTO iam_user(id,tenant_id,username,display_name,password_hash,status,force_password_change,password_changed_at)
VALUES (9101,9100,'admin','租户管理员','$2y$10$P7I1Cpqar23RGcyOjR1MNOQ7vIhgIP0aJlQ3hygqasaKJjO/yWbyC','ACTIVE',0,CURRENT_TIMESTAMP(3));
INSERT IGNORE INTO iam_user_organization(tenant_id,user_id,organization_id,is_primary)
VALUES (9100,9101,9101,1),(9100,9101,9102,0);
INSERT IGNORE INTO iam_user_context(tenant_id,user_id,current_organization_id) VALUES (9100,9101,9101);
INSERT IGNORE INTO iam_role(id,tenant_id,role_code,role_name,built_in,status)
VALUES (9101,9100,'TENANT_ADMIN','租户管理员',1,'ACTIVE');
INSERT IGNORE INTO iam_user_role(tenant_id,user_id,role_id) VALUES (9100,9101,9101);
INSERT IGNORE INTO iam_role_permission(tenant_id,role_id,permission_id)
SELECT 9100,9101,id FROM sys_permission WHERE status='ACTIVE';
INSERT IGNORE INTO iam_role_menu(tenant_id,role_id,menu_id)
SELECT 9100,9101,id FROM sys_menu WHERE status='ACTIVE';
INSERT IGNORE INTO iam_role_data_scope(tenant_id,role_id,resource_code,scope_type) VALUES
 (9100,9101,'iam:organization','TENANT_ALL'),(9100,9101,'iam:user','TENANT_ALL'),
 (9100,9101,'resource:file','TENANT_ALL'),(9100,9101,'print:template','TENANT_ALL');
