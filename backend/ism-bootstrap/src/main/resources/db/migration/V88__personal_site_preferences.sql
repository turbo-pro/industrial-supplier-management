CREATE TABLE iam_user_site_preference (
 tenant_id BIGINT NOT NULL,user_id BIGINT NOT NULL,preferred_site_id BIGINT NULL,
 version INT NOT NULL,updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 PRIMARY KEY(tenant_id,user_id),
 CONSTRAINT fk_site_pref_tenant FOREIGN KEY(tenant_id) REFERENCES iam_tenant(id),
 CONSTRAINT fk_site_pref_user FOREIGN KEY(user_id) REFERENCES iam_user(id),
 CONSTRAINT fk_site_pref_site FOREIGN KEY(preferred_site_id) REFERENCES iam_organization(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
INSERT INTO sys_permission(id,permission_code,permission_name,permission_type,status,module_code) VALUES
 (3070,'iam:site:view','查看个人授权场站','ACTION','ACTIVE','SYSTEM'),
 (3071,'iam:site:preference','设置个人首选场站','ACTION','ACTIVE','SYSTEM');
INSERT INTO sys_data_scope_resource_module(resource_code,module_code) VALUES('iam:site','SYSTEM');
INSERT INTO sys_menu(id,parent_id,module_code,menu_code,menu_name,route_path,component_key,icon,sort_order,status)
 VALUES(3130,3100,'SYSTEM','SITE_PREFERENCE','首选场站','/system/site-preference','SitePreferencePage','setting',60,'ACTIVE');
INSERT INTO iam_role_permission(tenant_id,role_id,permission_id)
 SELECT r.tenant_id,r.id,p.id FROM iam_role r JOIN sys_permission p ON p.id IN(3070,3071) WHERE r.role_code='TENANT_ADMIN' AND r.built_in=1;
INSERT INTO iam_role_menu(tenant_id,role_id,menu_id)
 SELECT tenant_id,id,3130 FROM iam_role WHERE role_code='TENANT_ADMIN' AND built_in=1;
INSERT INTO iam_role_data_scope(tenant_id,role_id,resource_code,scope_type)
 SELECT tenant_id,id,'iam:site','TENANT_ALL' FROM iam_role WHERE role_code='TENANT_ADMIN' AND built_in=1;
