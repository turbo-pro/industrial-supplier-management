ALTER TABLE sys_permission ADD COLUMN module_code VARCHAR(64) NULL DEFAULT NULL;

-- Explicit ownership for the shipped catalog. New unmapped permissions fail closed.
UPDATE sys_permission SET module_code='SYSTEM' WHERE permission_code IN (
 'iam:menu:view','iam:organization:manage','iam:organization:view','iam:role:manage',
 'iam:role:view','iam:user:manage','iam:user:view','system:dictionary:manage',
 'system:dictionary:view','system:setting:manage','system:setting:view');
UPDATE sys_permission SET module_code='COMMON' WHERE permission_code IN (
 'resource:file:download','resource:file:manage','resource:file:upload','resource:file:view',
 'print:execute','print:template:manage','print:template:view','search:global:use','search:saved:manage',
 'table:view:manage','table:view:publish','task:center:manage','task:center:operate','task:center:view');
UPDATE sys_permission SET module_code='MESSAGE' WHERE permission_code IN (
 'message:inbox:view','message:send','message:template:manage','message:template:view');
UPDATE sys_permission SET module_code='WORKFLOW' WHERE permission_code IN (
 'workflow:definition:manage','workflow:definition:view','workflow:instance:start','workflow:task:operate','workflow:task:view');
UPDATE sys_permission SET module_code='SUPPLIER' WHERE permission_code IN (
 'supplier:category:manage','supplier:master:create','supplier:master:status','supplier:master:update','supplier:master:view');
UPDATE sys_permission SET module_code='QUALIFICATION' WHERE permission_code IN (
 'supplier:admission:apply','supplier:admission:review','supplier:admission:view',
 'supplier:qualification:manage','supplier:qualification:type','supplier:qualification:verify','supplier:qualification:view');
UPDATE sys_permission SET module_code='PROJECT' WHERE permission_code IN (
 'contract:manage','contract:status','contract:view','project:manage','project:status','project:view');
UPDATE sys_permission SET module_code='RESOURCE' WHERE permission_code IN (
 'resource:asset:manage','resource:asset:status','resource:asset:view',
 'resource:person:id:view','resource:person:manage','resource:person:status','resource:person:view');
UPDATE sys_permission SET module_code='SAFETY' WHERE permission_code IN (
 'safety:attendance:checkin','safety:attendance:checkout','safety:attendance:view',
 'safety:credential:create','safety:credential:review','safety:credential:view',
 'safety:issue:create','safety:issue:rectify','safety:issue:verify','safety:issue:view');
UPDATE sys_permission SET module_code='QUALITY' WHERE permission_code IN (
 'quality:ncr:create','quality:ncr:rectify','quality:ncr:verify','quality:ncr:view');
UPDATE sys_permission SET module_code='PERFORMANCE' WHERE permission_code IN (
 'performance:evaluation:manage','performance:evaluation:review','performance:evaluation:view','performance:rule:manage');
UPDATE sys_permission SET module_code='RESTRICTION' WHERE permission_code IN (
 'supplier:appeal:create','supplier:appeal:review','supplier:appeal:view',
 'supplier:blacklist:manage','supplier:blacklist:review','supplier:blacklist:view',
 'supplier:exit:assign','supplier:exit:cancel','supplier:exit:create','supplier:exit:monitor',
 'supplier:exit:recheck','supplier:exit:remind','supplier:exit:review','supplier:exit:view',
 'supplier:finance:review','supplier:finance:submit','supplier:finance:view',
 'supplier:lift:create','supplier:lift:review','supplier:lift:view','supplier:restriction:explain');

-- The supplier navigation container can host independently subscribed submodules.
UPDATE sys_menu SET module_code='COMMON' WHERE menu_code='SUPPLIER_MANAGEMENT';
UPDATE sys_menu SET module_code='QUALIFICATION' WHERE menu_code='SUPPLIER_ADMISSION';
UPDATE sys_menu SET module_code='RESTRICTION'
WHERE menu_code IN ('SUPPLIER_BLACKLIST','MY_EXIT_TASKS','EXIT_MONITOR');
