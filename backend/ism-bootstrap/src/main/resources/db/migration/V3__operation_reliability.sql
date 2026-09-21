CREATE TABLE sys_idempotency_record (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    principal_id BIGINT NOT NULL,
    operation_code VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    response_status INT NULL,
    response_body JSON NULL,
    expires_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_idem (tenant_id,principal_id,operation_code,idempotency_key),
    KEY idx_sys_idem_expire (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_audit_event (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    operator_id BIGINT NOT NULL,
    acting_user_id BIGINT NULL,
    action VARCHAR(100) NOT NULL,
    object_type VARCHAR(64) NOT NULL,
    object_id BIGINT NOT NULL,
    before_summary JSON NULL,
    after_summary JSON NULL,
    ip VARCHAR(64) NULL,
    user_agent VARCHAR(500) NULL,
    trace_id VARCHAR(64) NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_audit_object (tenant_id,object_type,object_id,occurred_at),
    KEY idx_audit_operator (tenant_id,operator_id,occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_outbox_event (
    id BIGINT NOT NULL,
    event_id CHAR(36) NOT NULL,
    tenant_id BIGINT NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_version INT NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    payload JSON NOT NULL,
    status VARCHAR(20) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_at DATETIME(3) NOT NULL,
    lease_owner VARCHAR(100) NULL,
    lease_until DATETIME(3) NULL,
    last_error VARCHAR(500) NULL,
    occurred_at DATETIME(3) NOT NULL,
    published_at DATETIME(3) NULL,
    trace_id VARCHAR(64) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_outbox_event (event_id),
    KEY idx_outbox_dispatch (status,next_retry_at,lease_until,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_event_consumption (
    id BIGINT NOT NULL,
    event_id CHAR(36) NOT NULL,
    consumer_code VARCHAR(100) NOT NULL,
    consumed_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_event_consumer (event_id,consumer_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ops_async_task (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    task_no VARCHAR(64) NOT NULL,
    task_type VARCHAR(64) NOT NULL,
    requester_id BIGINT NOT NULL,
    request_payload JSON NOT NULL,
    status VARCHAR(24) NOT NULL,
    priority INT NOT NULL DEFAULT 0,
    progress INT NOT NULL DEFAULT 0,
    current_stage VARCHAR(100) NULL,
    lease_owner VARCHAR(100) NULL,
    lease_until DATETIME(3) NULL,
    retry_count INT NOT NULL DEFAULT 0,
    error_code VARCHAR(100) NULL,
    error_message VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    started_at DATETIME(3) NULL,
    finished_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_async_task_no (tenant_id,task_no),
    KEY idx_async_task_claim (status,lease_until,priority,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
