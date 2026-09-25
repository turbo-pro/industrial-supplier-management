CREATE TABLE per_score_rule (
 tenant_id BIGINT NOT NULL, quality_weight INT NOT NULL, delivery_weight INT NOT NULL,
 safety_weight INT NOT NULL, service_weight INT NOT NULL, version INT NOT NULL DEFAULT 0,
 updated_by BIGINT NOT NULL, updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 PRIMARY KEY(tenant_id), CONSTRAINT fk_per_rule_tenant FOREIGN KEY(tenant_id) REFERENCES iam_tenant(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE per_supplier_evaluation (
 id BIGINT NOT NULL, tenant_id BIGINT NOT NULL, organization_id BIGINT NOT NULL,
 supplier_id BIGINT NOT NULL, supplier_code VARCHAR(64) NOT NULL,
 supplier_name VARCHAR(200) NOT NULL, period_start DATE NOT NULL, period_end DATE NOT NULL,
 total_score DECIMAL(5,2) NOT NULL, grade VARCHAR(16) NOT NULL,
 status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
 quality_ncr_total INT NOT NULL DEFAULT 0, quality_ncr_open INT NOT NULL DEFAULT 0,
 safety_issue_total INT NOT NULL DEFAULT 0, safety_issue_open INT NOT NULL DEFAULT 0,
 submitted_at DATETIME(3) NULL, reviewed_by BIGINT NULL, reviewed_at DATETIME(3) NULL,
 review_comment VARCHAR(1000) NULL,
 created_by BIGINT NOT NULL, updated_by BIGINT NOT NULL, version INT NOT NULL DEFAULT 0,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id), UNIQUE KEY uk_per_supplier_period(tenant_id,supplier_id,period_start,period_end),
 KEY idx_per_supplier_status(tenant_id,supplier_id,status,period_end),
 CONSTRAINT fk_per_eval_tenant FOREIGN KEY(tenant_id) REFERENCES iam_tenant(id),
 CONSTRAINT fk_per_eval_supplier FOREIGN KEY(supplier_id) REFERENCES sup_supplier(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE per_evaluation_item (
 evaluation_id BIGINT NOT NULL, tenant_id BIGINT NOT NULL, dimension_code VARCHAR(24) NOT NULL,
 weight INT NOT NULL, score DECIMAL(5,2) NOT NULL, comment VARCHAR(1000) NOT NULL,
 evidence_file_id BIGINT NOT NULL,
 PRIMARY KEY(evaluation_id,dimension_code),
 CONSTRAINT fk_per_item_eval FOREIGN KEY(evaluation_id) REFERENCES per_supplier_evaluation(id),
 CONSTRAINT fk_per_item_file FOREIGN KEY(evidence_file_id) REFERENCES res_file_object(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE per_evaluation_event (
 id BIGINT NOT NULL, tenant_id BIGINT NOT NULL, evaluation_id BIGINT NOT NULL,
 action VARCHAR(24) NOT NULL, from_status VARCHAR(24) NULL, to_status VARCHAR(24) NOT NULL,
 comment VARCHAR(1000) NULL, actor_id BIGINT NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id), KEY idx_per_event(tenant_id,evaluation_id,created_at),
 CONSTRAINT fk_per_event_eval FOREIGN KEY(evaluation_id) REFERENCES per_supplier_evaluation(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO sys_permission(id,permission_code,permission_name,permission_type,status) VALUES
 (6701,'performance:evaluation:view','查看供应商绩效','ACTION','ACTIVE'),
 (6702,'performance:evaluation:manage','维护供应商绩效','ACTION','ACTIVE'),
 (6703,'performance:evaluation:review','审核供应商绩效','ACTION','ACTIVE'),
 (6704,'performance:rule:manage','配置绩效权重','ACTION','ACTIVE');
INSERT INTO sys_menu(id,parent_id,module_code,menu_code,menu_name,route_path,component_key,icon,sort_order,status) VALUES
 (6700,NULL,'PERFORMANCE','PERFORMANCE_MANAGEMENT','绩效评价','/performance','Layout','trend-charts',600,'ACTIVE'),
 (6701,6700,'PERFORMANCE','PERFORMANCE_EVALUATION','供应商绩效','/performance/evaluations','PerformanceEvaluationPage','data-analysis',10,'ACTIVE');
