CREATE TABLE sys_dictionary_type (
    type_code VARCHAR(64) NOT NULL,
    type_name VARCHAR(100) NOT NULL,
    tenant_extensible TINYINT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    PRIMARY KEY (type_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_dictionary_item (
    id BIGINT NOT NULL,
    type_code VARCHAR(64) NOT NULL,
    item_code VARCHAR(64) NOT NULL,
    item_label VARCHAR(100) NOT NULL,
    item_value VARCHAR(200) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_dictionary_item (type_code,item_code),
    CONSTRAINT fk_sys_dictionary_item_type FOREIGN KEY (type_code) REFERENCES sys_dictionary_type(type_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE cfg_tenant_dictionary_item (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    type_code VARCHAR(64) NOT NULL,
    item_code VARCHAR(64) NOT NULL,
    item_label VARCHAR(100) NOT NULL,
    item_value VARCHAR(200) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_cfg_dictionary_item (tenant_id,type_code,item_code),
    CONSTRAINT fk_cfg_dictionary_item_tenant FOREIGN KEY (tenant_id) REFERENCES iam_tenant(id),
    CONSTRAINT fk_cfg_dictionary_item_type FOREIGN KEY (type_code) REFERENCES sys_dictionary_type(type_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE cfg_setting_definition (
    setting_key VARCHAR(100) NOT NULL,
    setting_name VARCHAR(100) NOT NULL,
    value_type VARCHAR(20) NOT NULL,
    default_value VARCHAR(2000) NOT NULL,
    status VARCHAR(32) NOT NULL,
    PRIMARY KEY (setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE cfg_tenant_setting (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    setting_key VARCHAR(100) NOT NULL,
    value_type VARCHAR(20) NOT NULL,
    setting_value VARCHAR(2000) NOT NULL,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_cfg_tenant_setting (tenant_id,setting_key),
    CONSTRAINT fk_cfg_tenant_setting_tenant FOREIGN KEY (tenant_id) REFERENCES iam_tenant(id),
    CONSTRAINT fk_cfg_tenant_setting_definition FOREIGN KEY (setting_key) REFERENCES cfg_setting_definition(setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO cfg_setting_definition(setting_key,setting_name,value_type,default_value,status) VALUES
 ('branding.systemName','系统名称','STRING','工业供应商管理系统','ACTIVE'),
 ('branding.logoUrl','Logo 地址','URL','','ACTIVE'),
 ('branding.faviconUrl','站点图标地址','URL','','ACTIVE'),
 ('branding.footerText','页脚文字','STRING','','ACTIVE'),
 ('security.inactivePasswordDays','长期未登录改密天数','INTEGER','90','ACTIVE'),
 ('upload.maxFileSizeMb','单文件上限(MB)','INTEGER','100','ACTIVE');

INSERT INTO sys_dictionary_type(type_code,type_name,tenant_extensible,status) VALUES
 ('COMMON_STATUS','通用状态',0,'ACTIVE'),
 ('SUPPLIER_TYPE','供应商类型',1,'ACTIVE'),
 ('ORGANIZATION_TYPE','组织类型',0,'ACTIVE');

INSERT INTO sys_dictionary_item(id,type_code,item_code,item_label,item_value,sort_order,status) VALUES
 (4001,'COMMON_STATUS','ACTIVE','启用','ACTIVE',10,'ACTIVE'),
 (4002,'COMMON_STATUS','DISABLED','停用','DISABLED',20,'ACTIVE'),
 (4011,'SUPPLIER_TYPE','MATERIAL','物资供应商','MATERIAL',10,'ACTIVE'),
 (4012,'SUPPLIER_TYPE','SERVICE','服务供应商','SERVICE',20,'ACTIVE'),
 (4013,'SUPPLIER_TYPE','CONTRACTOR','承包商','CONTRACTOR',30,'ACTIVE'),
 (4021,'ORGANIZATION_TYPE','HEADQUARTERS','总部','HEADQUARTERS',10,'ACTIVE'),
 (4022,'ORGANIZATION_TYPE','SUBSIDIARY','子公司','SUBSIDIARY',20,'ACTIVE'),
 (4023,'ORGANIZATION_TYPE','SITE','工厂/场站','SITE',30,'ACTIVE'),
 (4024,'ORGANIZATION_TYPE','DEPARTMENT','部门','DEPARTMENT',40,'ACTIVE');

INSERT INTO sys_permission(id,permission_code,permission_name,permission_type,status) VALUES
 (4001,'system:dictionary:view','查看字典','ACTION','ACTIVE'),
 (4002,'system:dictionary:manage','管理租户字典','ACTION','ACTIVE'),
 (4003,'system:setting:view','查看租户配置','ACTION','ACTIVE'),
 (4004,'system:setting:manage','管理租户配置','ACTION','ACTIVE');

INSERT INTO sys_menu(id,parent_id,module_code,menu_code,menu_name,route_path,component_key,icon,sort_order,status) VALUES
 (4101,3100,'SYSTEM','DICTIONARY_MANAGEMENT','字典管理','/system/dictionaries','DictionaryPage','collection',40,'ACTIVE'),
 (4102,3100,'SYSTEM','TENANT_SETTING','租户配置','/system/settings','TenantSettingPage','tools',50,'ACTIVE');
