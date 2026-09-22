ALTER TABLE plt_tenant
    ADD COLUMN initialization_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' AFTER status,
    ADD COLUMN initialized_at DATETIME(3) NULL AFTER initialization_status;

ALTER TABLE iam_tenant
    ADD COLUMN timezone VARCHAR(50) NOT NULL DEFAULT 'Asia/Shanghai' AFTER status,
    ADD COLUMN locale VARCHAR(20) NOT NULL DEFAULT 'zh-CN' AFTER timezone;

CREATE TABLE iam_organization (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    parent_id BIGINT NULL,
    organization_code VARCHAR(64) NOT NULL,
    organization_name VARCHAR(200) NOT NULL,
    organization_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_iam_organization_code (tenant_id,organization_code),
    KEY idx_iam_organization_parent (tenant_id,parent_id),
    CONSTRAINT fk_iam_organization_tenant FOREIGN KEY (tenant_id) REFERENCES iam_tenant(id),
    CONSTRAINT fk_iam_organization_parent FOREIGN KEY (parent_id) REFERENCES iam_organization(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
