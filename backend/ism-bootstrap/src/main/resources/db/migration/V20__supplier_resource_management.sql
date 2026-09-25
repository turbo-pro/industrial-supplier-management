CREATE TABLE res_supplier_person (
 id BIGINT NOT NULL,tenant_id BIGINT NOT NULL,organization_id BIGINT NOT NULL,supplier_id BIGINT NOT NULL,project_id BIGINT NULL,person_code VARCHAR(64) NOT NULL,person_name VARCHAR(100) NOT NULL,
 id_type VARCHAR(24) NOT NULL,id_number_hash CHAR(64) NOT NULL,id_number_masked VARCHAR(64) NOT NULL,mobile VARCHAR(32) NULL,job_title VARCHAR(100) NULL,trade_type VARCHAR(100) NULL,
 entry_date DATE NULL,exit_date DATE NULL,status VARCHAR(24) NOT NULL DEFAULT 'PENDING',status_reason VARCHAR(500) NULL,created_by BIGINT NOT NULL,updated_by BIGINT NOT NULL,version INT NOT NULL DEFAULT 0,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),PRIMARY KEY(id),
 UNIQUE KEY uk_res_person_code(tenant_id,person_code),UNIQUE KEY uk_res_person_id_hash(tenant_id,id_number_hash),KEY idx_res_person_supplier(tenant_id,supplier_id,status),KEY idx_res_person_project(tenant_id,project_id,status),
 CONSTRAINT fk_res_person_tenant FOREIGN KEY(tenant_id) REFERENCES iam_tenant(id),CONSTRAINT fk_res_person_org FOREIGN KEY(organization_id) REFERENCES iam_organization(id),CONSTRAINT fk_res_person_supplier FOREIGN KEY(supplier_id) REFERENCES sup_supplier(id),CONSTRAINT fk_res_person_project FOREIGN KEY(project_id) REFERENCES prj_project(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE res_supplier_asset (
 id BIGINT NOT NULL,tenant_id BIGINT NOT NULL,organization_id BIGINT NOT NULL,supplier_id BIGINT NOT NULL,project_id BIGINT NULL,asset_code VARCHAR(64) NOT NULL,asset_name VARCHAR(150) NOT NULL,asset_type VARCHAR(24) NOT NULL,
 plate_no VARCHAR(32) NULL,serial_no VARCHAR(100) NULL,brand VARCHAR(100) NULL,model VARCHAR(100) NULL,inspection_expiry_date DATE NULL,file_id BIGINT NULL,
 status VARCHAR(24) NOT NULL DEFAULT 'PENDING',status_reason VARCHAR(500) NULL,created_by BIGINT NOT NULL,updated_by BIGINT NOT NULL,version INT NOT NULL DEFAULT 0,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),PRIMARY KEY(id),
 UNIQUE KEY uk_res_asset_code(tenant_id,asset_code),UNIQUE KEY uk_res_asset_plate(tenant_id,plate_no),KEY idx_res_asset_supplier(tenant_id,supplier_id,status),KEY idx_res_asset_project(tenant_id,project_id,status),
 CONSTRAINT fk_res_asset_tenant FOREIGN KEY(tenant_id) REFERENCES iam_tenant(id),CONSTRAINT fk_res_asset_org FOREIGN KEY(organization_id) REFERENCES iam_organization(id),CONSTRAINT fk_res_asset_supplier FOREIGN KEY(supplier_id) REFERENCES sup_supplier(id),CONSTRAINT fk_res_asset_project FOREIGN KEY(project_id) REFERENCES prj_project(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO sys_permission(id,permission_code,permission_name,permission_type,status) VALUES
 (6401,'resource:person:view','查看供应商人员','ACTION','ACTIVE'),(6402,'resource:person:manage','维护供应商人员','ACTION','ACTIVE'),(6403,'resource:person:status','变更人员状态','ACTION','ACTIVE'),(6404,'resource:person:id:view','查看人员证件号','FIELD','ACTIVE'),
 (6411,'resource:asset:view','查看车辆设备','ACTION','ACTIVE'),(6412,'resource:asset:manage','维护车辆设备','ACTION','ACTIVE'),(6413,'resource:asset:status','变更车辆设备状态','ACTION','ACTIVE');

INSERT INTO sys_menu(id,parent_id,module_code,menu_code,menu_name,route_path,component_key,icon,sort_order,status) VALUES
 (6400,NULL,'RESOURCE','SUPPLIER_RESOURCE','人员与设备','/resources','Layout','truck',300,'ACTIVE'),
 (6401,6400,'RESOURCE','SUPPLIER_PERSON','供应商人员','/resources/persons','SupplierPersonPage','user',10,'ACTIVE'),
 (6411,6400,'RESOURCE','SUPPLIER_ASSET','车辆设备','/resources/assets','SupplierAssetPage','van',20,'ACTIVE');
