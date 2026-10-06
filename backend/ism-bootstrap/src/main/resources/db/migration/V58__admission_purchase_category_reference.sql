ALTER TABLE qua_admission_application
 ADD COLUMN purchase_category_id BIGINT NULL AFTER purchase_category,
 ADD KEY idx_qua_admission_category (tenant_id,purchase_category_id),
 ADD CONSTRAINT fk_qua_admission_category FOREIGN KEY (purchase_category_id) REFERENCES sup_purchase_category(id);
