ALTER TABLE plt_subscription
    ADD COLUMN cancelled_at DATETIME(3) NULL,
    ADD COLUMN cancelled_by BIGINT NULL,
    ADD COLUMN cancel_reason VARCHAR(500) NULL;
