-- V25 must retain the checksum of its originally executed definition.
-- Snapshot columns are introduced separately and backfilled without dropping data.
ALTER TABLE per_supplier_evaluation
 ADD COLUMN supplier_code VARCHAR(64) NULL AFTER supplier_id,
 ADD COLUMN supplier_name VARCHAR(200) NULL AFTER supplier_code;

UPDATE per_supplier_evaluation e
JOIN sup_supplier s ON s.id=e.supplier_id AND s.tenant_id=e.tenant_id
SET e.supplier_code=s.supplier_code,e.supplier_name=s.supplier_name;

-- Missing same-tenant supplier references must fail, not acquire fabricated values.
ALTER TABLE per_supplier_evaluation
 MODIFY COLUMN supplier_code VARCHAR(64) NOT NULL,
 MODIFY COLUMN supplier_name VARCHAR(200) NOT NULL;
