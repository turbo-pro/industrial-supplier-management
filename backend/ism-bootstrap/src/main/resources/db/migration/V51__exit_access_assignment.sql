ALTER TABLE sup_exit_access_recovery_task
 ADD COLUMN assignee_id BIGINT NULL,
 ADD COLUMN due_date DATE NULL,
 ADD COLUMN assignment_note VARCHAR(1000) NULL,
 ADD COLUMN assigned_by BIGINT NULL,
 ADD COLUMN assigned_at DATETIME(3) NULL,
 ADD KEY idx_exit_access_assignee(tenant_id,assignee_id,due_date);

CREATE TABLE sup_exit_access_assignment_event (
 id BIGINT NOT NULL,tenant_id BIGINT NOT NULL,task_id BIGINT NOT NULL,
 previous_assignee_id BIGINT NULL,assignee_id BIGINT NOT NULL,due_date DATE NULL,
 note VARCHAR(1000) NOT NULL,actor_id BIGINT NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id),KEY idx_exit_access_assignment_event(tenant_id,task_id,created_at,id),
 CONSTRAINT fk_exit_access_assignment_task FOREIGN KEY(task_id) REFERENCES sup_exit_access_recovery_task(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
