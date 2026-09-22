ALTER TABLE ops_async_task
 ADD COLUMN cancel_requested TINYINT NOT NULL DEFAULT 0 AFTER current_stage,
 ADD COLUMN result_file_id BIGINT NULL AFTER cancel_requested,
 ADD KEY idx_async_task_requester (tenant_id,requester_id,created_at);
INSERT INTO sys_permission(id,permission_code,permission_name,permission_type,status) VALUES
 (5201,'task:center:view','查看个人任务','ACTION','ACTIVE'),(5202,'task:center:operate','取消和重试个人任务','ACTION','ACTIVE'),
 (5203,'task:center:manage','管理租户任务','ACTION','ACTIVE');
INSERT INTO sys_menu(id,parent_id,module_code,menu_code,menu_name,route_path,component_key,icon,sort_order,status) VALUES
 (5201,5000,'RESOURCE','TASK_CENTER','任务中心','/resources/tasks','TaskCenterPage','clock',20,'ACTIVE');
