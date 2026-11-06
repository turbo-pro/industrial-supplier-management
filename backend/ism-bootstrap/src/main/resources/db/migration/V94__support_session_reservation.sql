CREATE TABLE plt_support_session (
    id BIGINT NOT NULL PRIMARY KEY,
    request_id BIGINT NOT NULL,
    decision_id BIGINT NOT NULL,
    platform_operator_id BIGINT NOT NULL,
    target_tenant_id BIGINT NOT NULL,
    effective_role_id BIGINT NOT NULL,
    granted_scope_json JSON NOT NULL,
    created_at DATETIME(3) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    KEY idx_support_session_request (request_id, status),
    CONSTRAINT fk_support_session_request FOREIGN KEY (request_id) REFERENCES plt_support_request(id),
    CONSTRAINT fk_support_session_decision FOREIGN KEY (decision_id) REFERENCES plt_support_tenant_decision(id),
    CONSTRAINT fk_support_session_operator FOREIGN KEY (platform_operator_id) REFERENCES plt_user(id),
    CONSTRAINT fk_support_session_tenant FOREIGN KEY (target_tenant_id) REFERENCES plt_tenant(id),
    CONSTRAINT ck_support_session_expiry CHECK (expires_at > created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
