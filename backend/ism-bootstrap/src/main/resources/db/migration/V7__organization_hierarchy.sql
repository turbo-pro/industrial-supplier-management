ALTER TABLE iam_organization
    ADD COLUMN sort_order INT NOT NULL DEFAULT 0 AFTER status;

CREATE TABLE iam_organization_closure (
    tenant_id BIGINT NOT NULL,
    ancestor_id BIGINT NOT NULL,
    descendant_id BIGINT NOT NULL,
    depth INT NOT NULL,
    PRIMARY KEY (tenant_id,ancestor_id,descendant_id),
    KEY idx_iam_org_closure_descendant (tenant_id,descendant_id,depth),
    CONSTRAINT fk_iam_org_closure_tenant FOREIGN KEY (tenant_id) REFERENCES iam_tenant(id),
    CONSTRAINT fk_iam_org_closure_ancestor FOREIGN KEY (ancestor_id) REFERENCES iam_organization(id),
    CONSTRAINT fk_iam_org_closure_descendant FOREIGN KEY (descendant_id) REFERENCES iam_organization(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_user_organization (
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    organization_id BIGINT NOT NULL,
    is_primary TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id,user_id,organization_id),
    KEY idx_iam_user_org_org (tenant_id,organization_id,user_id),
    CONSTRAINT fk_iam_user_org_tenant FOREIGN KEY (tenant_id) REFERENCES iam_tenant(id),
    CONSTRAINT fk_iam_user_org_user FOREIGN KEY (user_id) REFERENCES iam_user(id),
    CONSTRAINT fk_iam_user_org_organization FOREIGN KEY (organization_id) REFERENCES iam_organization(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_user_context (
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    current_organization_id BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (tenant_id,user_id),
    CONSTRAINT fk_iam_user_context_tenant FOREIGN KEY (tenant_id) REFERENCES iam_tenant(id),
    CONSTRAINT fk_iam_user_context_user FOREIGN KEY (user_id) REFERENCES iam_user(id),
    CONSTRAINT fk_iam_user_context_organization FOREIGN KEY (current_organization_id) REFERENCES iam_organization(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO iam_organization_closure(tenant_id,ancestor_id,descendant_id,depth)
SELECT tenant_id,id,id,0 FROM iam_organization;
