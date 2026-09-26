ALTER TABLE res_supplier_asset
 ADD COLUMN handed_over_at DATETIME(3) NULL,
 ADD COLUMN handed_over_by BIGINT NULL,
 ADD COLUMN handover_recipient VARCHAR(100) NULL,
 ADD COLUMN handover_note VARCHAR(1000) NULL,
 ADD INDEX idx_asset_handover (tenant_id,supplier_id,handed_over_at);
