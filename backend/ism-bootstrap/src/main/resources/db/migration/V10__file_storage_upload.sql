CREATE TABLE res_upload_session (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    uploader_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(150) NOT NULL,
    total_size BIGINT NOT NULL,
    file_sha256 CHAR(64) NOT NULL,
    chunk_size INT NOT NULL,
    total_chunks INT NOT NULL,
    status VARCHAR(24) NOT NULL,
    file_id BIGINT NULL,
    expires_at DATETIME(3) NOT NULL,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_upload_owner (tenant_id,uploader_id,status,created_at),
    KEY idx_upload_expire (status,expires_at),
    CONSTRAINT fk_upload_tenant FOREIGN KEY (tenant_id) REFERENCES iam_tenant(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE res_upload_chunk (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    upload_id BIGINT NOT NULL,
    chunk_index INT NOT NULL,
    chunk_size INT NOT NULL,
    chunk_sha256 CHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_upload_chunk (tenant_id,upload_id,chunk_index),
    CONSTRAINT fk_chunk_tenant FOREIGN KEY (tenant_id) REFERENCES iam_tenant(id),
    CONSTRAINT fk_chunk_upload FOREIGN KEY (upload_id) REFERENCES res_upload_session(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE res_file_object (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    owner_id BIGINT NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(150) NOT NULL,
    file_size BIGINT NOT NULL,
    file_sha256 CHAR(64) NOT NULL,
    storage_provider VARCHAR(24) NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    status VARCHAR(24) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_file_hash (tenant_id,file_sha256,file_size),
    KEY idx_file_owner (tenant_id,owner_id,status,created_at),
    CONSTRAINT fk_file_tenant FOREIGN KEY (tenant_id) REFERENCES iam_tenant(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO sys_permission(id,permission_code,permission_name,permission_type,status) VALUES
 (5001,'resource:file:view','查看文件元数据','ACTION','ACTIVE'),
 (5002,'resource:file:upload','上传文件','ACTION','ACTIVE'),
 (5003,'resource:file:download','下载文件','ACTION','ACTIVE'),
 (5004,'resource:file:manage','删除和管理文件','ACTION','ACTIVE');

INSERT INTO sys_menu(id,parent_id,module_code,menu_code,menu_name,route_path,component_key,icon,sort_order,status) VALUES
 (5000,NULL,'RESOURCE','RESOURCE_CENTER','资源中心','/resources','Layout','folder',700,'ACTIVE'),
 (5001,5000,'RESOURCE','FILE_MANAGEMENT','文件管理','/resources/files','FileManagementPage','document',10,'ACTIVE');
