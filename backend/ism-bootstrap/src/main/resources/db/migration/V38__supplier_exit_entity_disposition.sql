CREATE TABLE sup_exit_entity (
 id BIGINT NOT NULL,tenant_id BIGINT NOT NULL,application_id BIGINT NOT NULL,
 check_code VARCHAR(64) NOT NULL,source_id BIGINT NOT NULL,route VARCHAR(200) NOT NULL,
 state VARCHAR(16) NOT NULL DEFAULT 'OPEN',assignee_id BIGINT NULL,note VARCHAR(2000) NULL,
 assigned_by BIGINT NULL,assigned_at DATETIME(3) NULL,
 checked_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),cleared_at DATETIME(3) NULL,
 version INT NOT NULL DEFAULT 0,
 PRIMARY KEY(id),UNIQUE KEY uk_exit_entity(tenant_id,application_id,check_code,source_id),
 KEY idx_exit_entity_assignee(tenant_id,assignee_id,state),
 CONSTRAINT fk_exit_entity_application FOREIGN KEY(application_id) REFERENCES sup_exit_application(id),
 CONSTRAINT chk_exit_entity_state CHECK(state IN ('OPEN','CLEARED')),
 CONSTRAINT chk_exit_entity_source CHECK(source_id>0),
 CONSTRAINT chk_exit_entity_version CHECK(version>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO sys_permission(id,permission_code,permission_name,permission_type,status)
VALUES (7501,'supplier:exit:assign','分派退出处置责任与记录处理说明','ACTION','ACTIVE');
