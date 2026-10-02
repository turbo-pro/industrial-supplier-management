ALTER TABLE sup_exit_access_recovery_task
 ADD COLUMN reminder_failure_count INT NOT NULL DEFAULT 0,
 ADD COLUMN reminder_failure_code VARCHAR(64) NULL,
 ADD COLUMN reminder_failure_status VARCHAR(16) NULL,
 ADD COLUMN reminder_first_failed_at DATETIME(3) NULL,
 ADD COLUMN reminder_last_failed_at DATETIME(3) NULL,
 ADD COLUMN reminder_resolved_at DATETIME(3) NULL;
