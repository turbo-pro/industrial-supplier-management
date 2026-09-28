-- owner_id=0 is reserved for tenant-shared schemes in the existing view/lock tables.
-- It is not an IAM user and never authorizes a request by itself.
INSERT INTO sys_permission(id,permission_code,permission_name,permission_type,status)
VALUES(7901,'table:view:publish','发布租户共享表格列方案','ACTION','ACTIVE');
