CREATE TABLE plt_support_request (
 id BIGINT NOT NULL PRIMARY KEY,
 biz_no VARCHAR(64) NOT NULL,
 platform_operator_id BIGINT NOT NULL,
 target_tenant_id BIGINT NOT NULL,
 ticket_no VARCHAR(100) NOT NULL,
 reason VARCHAR(1000) NOT NULL,
 profile_json JSON NOT NULL,
 requested_expire_at DATETIME(3) NOT NULL,
 status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
 submitted_at DATETIME(3) NULL,
 version INT NOT NULL DEFAULT 0,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE KEY uk_support_request_biz (biz_no),
 KEY idx_support_request_owner (platform_operator_id,created_at,id),
 KEY idx_support_request_queue (status,requested_expire_at,created_at,id),
 CONSTRAINT fk_support_request_user FOREIGN KEY(platform_operator_id) REFERENCES plt_user(id),
 CONSTRAINT fk_support_request_tenant FOREIGN KEY(target_tenant_id) REFERENCES plt_tenant(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE plt_support_approval (
 id BIGINT NOT NULL PRIMARY KEY,request_id BIGINT NOT NULL,operator_id BIGINT NOT NULL,
 reviewer_id BIGINT NOT NULL,decision VARCHAR(16) NOT NULL,comment VARCHAR(1000) NOT NULL,
 reviewed_at DATETIME(3) NOT NULL,
 UNIQUE KEY uk_support_approval_request(request_id),
 CONSTRAINT ck_support_independent_review CHECK(operator_id <> reviewer_id),
 CONSTRAINT fk_support_approval_request FOREIGN KEY(request_id) REFERENCES plt_support_request(id),
 CONSTRAINT fk_support_approval_reviewer FOREIGN KEY(reviewer_id) REFERENCES plt_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE plt_support_request_event (
 id BIGINT NOT NULL PRIMARY KEY,request_id BIGINT NOT NULL,actor_id BIGINT NOT NULL,
 request_version INT NOT NULL,action VARCHAR(32) NOT NULL,comment VARCHAR(1000) NOT NULL,occurred_at DATETIME(3) NOT NULL,
 UNIQUE KEY uk_support_event_version(request_id,request_version),
 CONSTRAINT fk_support_event_request FOREIGN KEY(request_id) REFERENCES plt_support_request(id),
 CONSTRAINT fk_support_event_actor FOREIGN KEY(actor_id) REFERENCES plt_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
INSERT INTO plt_permission(id,permission_code,permission_name,status) VALUES
 (1124,'platform:support:view','查看支持申请','ACTIVE'),
 (1125,'platform:support:review','独立审批支持申请','ACTIVE');
INSERT INTO plt_role_permission(role_id,permission_id) VALUES(1001,1124),(1001,1125),(1002,1124);
