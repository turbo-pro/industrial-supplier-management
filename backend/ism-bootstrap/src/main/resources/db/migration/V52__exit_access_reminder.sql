ALTER TABLE sup_exit_access_recovery_task
 ADD COLUMN last_reminded_at DATETIME(3) NULL;

CREATE TABLE sup_exit_access_reminder_event (
 id BIGINT NOT NULL,tenant_id BIGINT NOT NULL,task_id BIGINT NOT NULL,
 recipient_id BIGINT NOT NULL,actor_id BIGINT NOT NULL,
 due_date DATE NULL,created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id),KEY idx_exit_access_reminder_event(tenant_id,task_id,created_at,id),
 CONSTRAINT fk_exit_access_reminder_task FOREIGN KEY(task_id) REFERENCES sup_exit_access_recovery_task(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
