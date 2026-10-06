CREATE TABLE sup_purchase_category_admission_material (
 tenant_id BIGINT NOT NULL,category_id BIGINT NOT NULL,material_type VARCHAR(64) NOT NULL,
 material_name VARCHAR(100) NOT NULL,sort_order INT NOT NULL DEFAULT 0,
 PRIMARY KEY(tenant_id,category_id,material_type),
 KEY idx_sup_category_material_category(tenant_id,category_id,sort_order),
 CONSTRAINT fk_sup_category_material_tenant FOREIGN KEY(tenant_id) REFERENCES iam_tenant(id),
 CONSTRAINT fk_sup_category_material_category FOREIGN KEY(category_id) REFERENCES sup_purchase_category(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
