ALTER TABLE plt_subscription ADD COLUMN assignment_sequence BIGINT NOT NULL DEFAULT 0;

CREATE INDEX idx_plt_subscription_validity
    ON plt_subscription(tenant_id,status,effective_from,effective_to);
