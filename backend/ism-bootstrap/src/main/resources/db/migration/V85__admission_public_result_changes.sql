-- Original published business facts remain immutable; only the change cursor advances.
ALTER TABLE qua_admission_public_result ADD COLUMN change_version INT NOT NULL DEFAULT 0;
CREATE TABLE qua_admission_result_change (
 id BIGINT NOT NULL,tenant_id BIGINT NOT NULL,publication_id BIGINT NOT NULL,change_no INT NOT NULL,
 kind VARCHAR(20) NOT NULL,public_comment VARCHAR(1000) NULL,public_reason VARCHAR(500) NOT NULL,
 changed_by BIGINT NOT NULL,changed_at DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)),
 PRIMARY KEY(id),UNIQUE KEY uk_admission_result_change(publication_id,change_no),
 KEY idx_admission_result_change(tenant_id,publication_id,change_no),
 CONSTRAINT fk_admission_result_change FOREIGN KEY(publication_id) REFERENCES qua_admission_public_result(id),
 CONSTRAINT ck_admission_result_change CHECK(change_no>0 AND ((kind='CORRECT' AND public_comment IS NOT NULL) OR (kind='WITHDRAW' AND public_comment IS NULL)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
