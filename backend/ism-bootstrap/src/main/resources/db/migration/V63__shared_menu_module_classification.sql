-- Shared infrastructure must remain available independently of the billable
-- RESOURCE module (supplier people, vehicles and equipment).
UPDATE sys_menu SET module_code='COMMON'
WHERE menu_code IN ('RESOURCE_CENTER','FILE_MANAGEMENT','TASK_CENTER','PRINT_TEMPLATE','ADVANCED_SEARCH')
  AND module_code='RESOURCE';
