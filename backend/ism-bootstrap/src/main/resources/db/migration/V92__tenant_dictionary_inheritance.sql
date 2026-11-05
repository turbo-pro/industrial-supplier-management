ALTER TABLE cfg_tenant_dictionary_item ADD COLUMN overridden TINYINT NOT NULL DEFAULT 1;
CREATE TABLE cfg_tenant_dictionary_type (
 tenant_id BIGINT NOT NULL,type_code VARCHAR(64) NOT NULL,display_name VARCHAR(100) NULL,
 overridden TINYINT NOT NULL DEFAULT 0,version INT NOT NULL DEFAULT 0,
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 PRIMARY KEY(tenant_id,type_code),
 CONSTRAINT fk_cfg_type_tenant FOREIGN KEY(tenant_id) REFERENCES iam_tenant(id),
 CONSTRAINT fk_cfg_type_system FOREIGN KEY(type_code) REFERENCES sys_dictionary_type(type_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
