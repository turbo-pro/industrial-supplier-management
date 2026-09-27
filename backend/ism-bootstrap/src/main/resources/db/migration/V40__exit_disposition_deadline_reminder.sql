ALTER TABLE sup_exit_entity ADD COLUMN due_date DATE NULL,
 ADD COLUMN last_reminded_at DATETIME(3) NULL;
INSERT INTO sys_permission(id,permission_code,permission_name,permission_type,status)
VALUES(7701,'supplier:exit:remind','催办退出处置责任人','ACTION','ACTIVE');
