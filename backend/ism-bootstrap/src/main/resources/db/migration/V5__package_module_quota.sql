CREATE TABLE plt_module (
    id BIGINT NOT NULL,
    module_code VARCHAR(64) NOT NULL,
    module_name VARCHAR(100) NOT NULL,
    status VARCHAR(32) NOT NULL,
    sort_order INT NOT NULL,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_plt_module_code (module_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE plt_package (
    id BIGINT NOT NULL,
    package_code VARCHAR(64) NOT NULL,
    package_name VARCHAR(100) NOT NULL,
    status VARCHAR(32) NOT NULL,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_plt_package_code (package_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE plt_package_version (
    id BIGINT NOT NULL,
    package_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    version_name VARCHAR(100) NOT NULL,
    effective_from DATETIME(3) NULL,
    status VARCHAR(32) NOT NULL,
    published_at DATETIME(3) NULL,
    published_by BIGINT NULL,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_plt_package_version (package_id,version_no),
    CONSTRAINT fk_plt_package_version_package FOREIGN KEY (package_id) REFERENCES plt_package(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE plt_package_module (
    package_version_id BIGINT NOT NULL,
    module_id BIGINT NOT NULL,
    enabled TINYINT NOT NULL,
    quota_json JSON NOT NULL,
    PRIMARY KEY (package_version_id,module_id),
    CONSTRAINT fk_plt_package_module_version FOREIGN KEY (package_version_id) REFERENCES plt_package_version(id),
    CONSTRAINT fk_plt_package_module_module FOREIGN KEY (module_id) REFERENCES plt_module(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE plt_tenant (
    id BIGINT NOT NULL,
    tenant_code VARCHAR(50) NOT NULL,
    tenant_name VARCHAR(200) NOT NULL,
    status VARCHAR(32) NOT NULL,
    timezone VARCHAR(50) NOT NULL DEFAULT 'Asia/Shanghai',
    locale VARCHAR(20) NOT NULL DEFAULT 'zh-CN',
    package_version_id BIGINT NULL,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_plt_tenant_code (tenant_code),
    CONSTRAINT fk_plt_tenant_package_version FOREIGN KEY (package_version_id) REFERENCES plt_package_version(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE plt_subscription (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    package_version_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    effective_from DATETIME(3) NOT NULL,
    effective_to DATETIME(3) NULL,
    exception_json JSON NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_plt_subscription_tenant_status (tenant_id,status),
    CONSTRAINT fk_plt_subscription_tenant FOREIGN KEY (tenant_id) REFERENCES plt_tenant(id),
    CONSTRAINT fk_plt_subscription_version FOREIGN KEY (package_version_id) REFERENCES plt_package_version(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE plt_quota_usage (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    quota_code VARCHAR(64) NOT NULL,
    period_key VARCHAR(32) NOT NULL,
    used_value BIGINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_plt_quota_usage (tenant_id,quota_code,period_key),
    CONSTRAINT fk_plt_quota_usage_tenant FOREIGN KEY (tenant_id) REFERENCES plt_tenant(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO plt_module(id,module_code,module_name,status,sort_order) VALUES
 (2001,'SUPPLIER','供应商生命周期','ACTIVE',10),(2002,'QUALIFICATION','准入与资格','ACTIVE',20),
 (2003,'PROJECT','合同与项目','ACTIVE',30),(2004,'RESOURCE','人员车辆设备','ACTIVE',40),
 (2005,'SAFETY','培训入场与现场安全','ACTIVE',50),(2006,'QUALITY','质量与CAPA','ACTIVE',60),
 (2007,'PERFORMANCE','绩效评价','ACTIVE',70),(2008,'RESTRICTION','限制与黑名单','ACTIVE',80),
 (2009,'INTEGRATION','外部集成','ACTIVE',90);
