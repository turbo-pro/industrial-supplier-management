INSERT INTO sys_permission(id,permission_code,permission_name,permission_type,status)
VALUES(8001,'supplier:exit:monitor','跨供应商查看退出处置监控','ACTION','ACTIVE');

INSERT INTO sys_menu(id,parent_id,module_code,menu_code,menu_name,route_path,component_key,icon,sort_order,status)
VALUES(8002,6000,'SUPPLIER','EXIT_MONITOR','退出处置监控','/suppliers/exit-monitor','ExitMonitorPage','list',91,'ACTIVE');

CREATE INDEX idx_exit_entity_monitor ON sup_exit_entity(tenant_id,state,due_date,id);
