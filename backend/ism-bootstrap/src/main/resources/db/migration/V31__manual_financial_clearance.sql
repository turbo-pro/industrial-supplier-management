CREATE TABLE int_financial_clearance (
 tenant_id BIGINT NOT NULL, supplier_id BIGINT NOT NULL,
 status VARCHAR(16) NOT NULL, evidence_file_id BIGINT NOT NULL,
 statement VARCHAR(2000) NOT NULL, submitted_by BIGINT NOT NULL,
 submitted_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 reviewed_by BIGINT NULL, reviewed_at DATETIME(3) NULL,
 review_comment VARCHAR(1000) NULL, version INT NOT NULL DEFAULT 0,
 PRIMARY KEY(tenant_id,supplier_id),
 CONSTRAINT fk_clearance_tenant FOREIGN KEY(tenant_id) REFERENCES iam_tenant(id),
 CONSTRAINT fk_clearance_supplier FOREIGN KEY(supplier_id) REFERENCES sup_supplier(id),
 CONSTRAINT fk_clearance_file FOREIGN KEY(evidence_file_id) REFERENCES res_file_object(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO sys_permission(id,permission_code,permission_name,permission_type,status) VALUES
 (6901,'supplier:finance:view','查看人工财务核验','ACTION','ACTIVE'),
 (6902,'supplier:finance:submit','提交人工财务核验','ACTION','ACTIVE'),
 (6903,'supplier:finance:review','复核和撤销人工财务核验','ACTION','ACTIVE');
