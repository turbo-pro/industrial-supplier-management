ALTER TABLE sup_blacklist_case
 ADD COLUMN restriction_type VARCHAR(24) NOT NULL DEFAULT 'BLACKLIST' AFTER supplier_name,
 ADD COLUMN effective_from DATE NULL AFTER restriction_type,
 ADD COLUMN effective_until DATE NULL AFTER effective_from,
 ADD KEY idx_sup_restriction_effective(tenant_id,supplier_id,status,restriction_type,effective_from,effective_until);

UPDATE sys_menu SET menu_name='限制与黑名单' WHERE id=6801;
