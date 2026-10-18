CREATE TABLE plt_subscription_reminder (
    tenant_id BIGINT NOT NULL,
    subscription_id BIGINT NOT NULL,
    stage VARCHAR(24) NOT NULL,
    status VARCHAR(24) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    recipient_count INT NOT NULL DEFAULT 0,
    attempted_at DATETIME(3) NOT NULL,
    sent_at DATETIME(3) NULL,
    error_code VARCHAR(100) NULL,
    PRIMARY KEY(subscription_id,stage),
    KEY idx_subscription_reminder_tenant(tenant_id,subscription_id),
    CONSTRAINT fk_subscription_reminder_subscription FOREIGN KEY(subscription_id) REFERENCES plt_subscription(id),
    CONSTRAINT fk_subscription_reminder_tenant FOREIGN KEY(tenant_id) REFERENCES plt_tenant(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
