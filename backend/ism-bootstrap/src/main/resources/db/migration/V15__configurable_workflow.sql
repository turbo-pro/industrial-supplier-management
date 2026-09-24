CREATE TABLE wf_definition (
 id BIGINT NOT NULL,tenant_id BIGINT NOT NULL,definition_code VARCHAR(64) NOT NULL,definition_name VARCHAR(100) NOT NULL,business_type VARCHAR(64) NOT NULL,
 draft_bpmn MEDIUMTEXT NOT NULL,status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',current_version INT NOT NULL DEFAULT 0,version INT NOT NULL DEFAULT 0,
 created_by BIGINT NOT NULL,updated_by BIGINT NOT NULL,created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id),UNIQUE KEY uk_wf_definition_code(tenant_id,definition_code),KEY idx_wf_definition_business(tenant_id,business_type,status),
 CONSTRAINT fk_wf_definition_tenant FOREIGN KEY(tenant_id) REFERENCES iam_tenant(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE wf_definition_version (
 id BIGINT NOT NULL,tenant_id BIGINT NOT NULL,definition_id BIGINT NOT NULL,definition_version INT NOT NULL,bpmn_xml MEDIUMTEXT NOT NULL,
 deployment_id VARCHAR(64) NOT NULL,process_definition_id VARCHAR(128) NOT NULL,published_by BIGINT NOT NULL,published_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id),UNIQUE KEY uk_wf_definition_version(tenant_id,definition_id,definition_version),UNIQUE KEY uk_wf_process_definition(tenant_id,process_definition_id),
 CONSTRAINT fk_wf_version_tenant FOREIGN KEY(tenant_id) REFERENCES iam_tenant(id),CONSTRAINT fk_wf_version_definition FOREIGN KEY(definition_id) REFERENCES wf_definition(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE wf_business_instance (
 id BIGINT NOT NULL,tenant_id BIGINT NOT NULL,definition_id BIGINT NOT NULL,definition_version INT NOT NULL,process_instance_id VARCHAR(64) NOT NULL,
 starter_id BIGINT NOT NULL,business_type VARCHAR(64) NOT NULL,business_id VARCHAR(128) NOT NULL,status VARCHAR(20) NOT NULL DEFAULT 'RUNNING',result VARCHAR(20) NULL,
 started_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),finished_at DATETIME(3) NULL,
 PRIMARY KEY(id),UNIQUE KEY uk_wf_process_instance(tenant_id,process_instance_id),KEY idx_wf_business(tenant_id,business_type,business_id),KEY idx_wf_starter(tenant_id,starter_id,status),
 CONSTRAINT fk_wf_instance_tenant FOREIGN KEY(tenant_id) REFERENCES iam_tenant(id),CONSTRAINT fk_wf_instance_definition FOREIGN KEY(definition_id) REFERENCES wf_definition(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO sys_permission(id,permission_code,permission_name,permission_type,status) VALUES
 (5501,'workflow:definition:view','查看流程定义','ACTION','ACTIVE'),
 (5502,'workflow:definition:manage','管理和发布流程定义','ACTION','ACTIVE'),
 (5503,'workflow:instance:start','发起流程实例','ACTION','ACTIVE'),
 (5504,'workflow:task:view','查看我的流程待办','ACTION','ACTIVE'),
 (5505,'workflow:task:operate','认领和办理流程待办','ACTION','ACTIVE');

INSERT INTO sys_menu(id,parent_id,module_code,menu_code,menu_name,route_path,component_key,icon,sort_order,status) VALUES
 (5500,NULL,'WORKFLOW','WORKFLOW_CENTER','流程中心','/workflow','Layout','connection',600,'ACTIVE'),
 (5501,5500,'WORKFLOW','WORKFLOW_DEFINITION','流程定义','/workflow/definitions','WorkflowDefinitionPage','setting',10,'ACTIVE'),
 (5502,5500,'WORKFLOW','WORKFLOW_TASK','我的待办','/workflow/tasks','WorkflowTaskPage','check',20,'ACTIVE');
