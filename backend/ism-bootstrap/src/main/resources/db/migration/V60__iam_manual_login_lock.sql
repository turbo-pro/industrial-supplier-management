ALTER TABLE iam_user
    ADD COLUMN manual_locked TINYINT NOT NULL DEFAULT 0,
    ADD COLUMN manual_lock_reason VARCHAR(500) NULL,
    ADD COLUMN manual_locked_at DATETIME(3) NULL,
    ADD COLUMN manual_locked_by BIGINT NULL;
