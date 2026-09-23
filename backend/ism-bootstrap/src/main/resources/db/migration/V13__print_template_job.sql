CREATE TABLE prt_template (
 id BIGINT NOT NULL,tenant_id BIGINT NOT NULL,template_code VARCHAR(64) NOT NULL,template_name VARCHAR(100) NOT NULL,
 business_type VARCHAR(64) NOT NULL,draft_html MEDIUMTEXT NOT NULL,variable_schema JSON NOT NULL,page_config JSON NOT NULL,
 status VARCHAR(24) NOT NULL,current_version INT NOT NULL DEFAULT 0,version INT NOT NULL DEFAULT 0,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id),UNIQUE KEY uk_prt_template(tenant_id,template_code),CONSTRAINT fk_prt_template_tenant FOREIGN KEY(tenant_id) REFERENCES iam_tenant(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE prt_template_version (
 id BIGINT NOT NULL,tenant_id BIGINT NOT NULL,template_id BIGINT NOT NULL,version_no INT NOT NULL,html_content MEDIUMTEXT NOT NULL,
 variable_schema JSON NOT NULL,page_config JSON NOT NULL,published_by BIGINT NOT NULL,published_at DATETIME(3) NOT NULL,
 PRIMARY KEY(id),UNIQUE KEY uk_prt_template_version(tenant_id,template_id,version_no),
 CONSTRAINT fk_prt_version_tenant FOREIGN KEY(tenant_id) REFERENCES iam_tenant(id),CONSTRAINT fk_prt_version_template FOREIGN KEY(template_id) REFERENCES prt_template(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE prt_print_job (
 id BIGINT NOT NULL,tenant_id BIGINT NOT NULL,task_id BIGINT NOT NULL,template_version_id BIGINT NOT NULL,requester_id BIGINT NOT NULL,
 business_type VARCHAR(64) NULL,business_id BIGINT NULL,render_payload JSON NOT NULL,created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id),UNIQUE KEY uk_prt_job_task(tenant_id,task_id),CONSTRAINT fk_prt_job_tenant FOREIGN KEY(tenant_id) REFERENCES iam_tenant(id),
 CONSTRAINT fk_prt_job_version FOREIGN KEY(template_version_id) REFERENCES prt_template_version(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
INSERT INTO sys_permission(id,permission_code,permission_name,permission_type,status) VALUES
 (5301,'print:template:view','查看打印模板','ACTION','ACTIVE'),(5302,'print:template:manage','管理打印模板','ACTION','ACTIVE'),(5303,'print:execute','发起打印任务','ACTION','ACTIVE');
INSERT INTO sys_menu(id,parent_id,module_code,menu_code,menu_name,route_path,component_key,icon,sort_order,status) VALUES
 (5301,5000,'RESOURCE','PRINT_TEMPLATE','打印模板','/resources/print-templates','PrintTemplatePage','printer',30,'ACTIVE');
