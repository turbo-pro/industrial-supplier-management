CREATE TABLE saf_site_attendance (
 id BIGINT NOT NULL, tenant_id BIGINT NOT NULL, organization_id BIGINT NOT NULL,
 project_id BIGINT NOT NULL, supplier_id BIGINT NOT NULL, person_id BIGINT NOT NULL,
 site_name VARCHAR(200) NOT NULL, check_in_at DATETIME(3) NOT NULL,
 check_out_at DATETIME(3) NULL, check_in_by BIGINT NOT NULL, check_out_by BIGINT NULL,
 check_out_note VARCHAR(500) NULL, created_by BIGINT NOT NULL,
 open_person_id BIGINT GENERATED ALWAYS AS (CASE WHEN check_out_at IS NULL THEN person_id ELSE NULL END) STORED,
 version INT NOT NULL DEFAULT 0,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id), UNIQUE KEY uk_saf_attendance_open(tenant_id,open_person_id),
 KEY idx_saf_attendance_person(tenant_id,person_id,check_in_at),
 KEY idx_saf_attendance_project(tenant_id,project_id,check_in_at),
 CONSTRAINT fk_saf_attendance_tenant FOREIGN KEY(tenant_id) REFERENCES iam_tenant(id),
 CONSTRAINT fk_saf_attendance_person FOREIGN KEY(person_id) REFERENCES res_supplier_person(id),
 CONSTRAINT fk_saf_attendance_project FOREIGN KEY(project_id) REFERENCES prj_project(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO sys_permission(id,permission_code,permission_name,permission_type,status) VALUES
 (6521,'safety:attendance:view','查看现场出入记录','ACTION','ACTIVE'),
 (6522,'safety:attendance:checkin','人员现场签到','ACTION','ACTIVE'),
 (6523,'safety:attendance:checkout','人员现场签退','ACTION','ACTIVE');
INSERT INTO sys_menu(id,parent_id,module_code,menu_code,menu_name,route_path,component_key,icon,sort_order,status) VALUES
 (6521,6500,'SAFETY','SAFETY_ATTENDANCE','现场出入','/safety/attendance','SafetyAttendancePage','location',30,'ACTIVE');
