CREATE TABLE sup_restriction_gate_hit (
 id BIGINT NOT NULL,tenant_id BIGINT NOT NULL,supplier_id BIGINT NOT NULL,
 actor_id BIGINT NOT NULL,action_code VARCHAR(40) NOT NULL,decision VARCHAR(8) NOT NULL,
 hit_code VARCHAR(40) NOT NULL,source_id VARCHAR(40) NOT NULL,
 business_date DATE NOT NULL,created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 PRIMARY KEY(id),KEY idx_gate_hit_supplier(tenant_id,supplier_id,created_at,id),
 CONSTRAINT chk_gate_hit_decision CHECK(decision IN ('WARN','DENY'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
