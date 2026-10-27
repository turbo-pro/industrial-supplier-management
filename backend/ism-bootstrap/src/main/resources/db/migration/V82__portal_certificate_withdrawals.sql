-- Withdrawal is a terminal business action; preserve submission, uploads and correction history.
CREATE TABLE qua_portal_withdrawal (
 qualification_id BIGINT NOT NULL,tenant_id BIGINT NOT NULL,supplier_id BIGINT NOT NULL,account_id BIGINT NOT NULL,
 source_version INT NOT NULL,reason VARCHAR(500) NOT NULL,withdrawn_at DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)),
 PRIMARY KEY(qualification_id),KEY idx_portal_withdrawal_supplier(tenant_id,supplier_id,withdrawn_at),
 CONSTRAINT fk_portal_withdrawal_submission FOREIGN KEY(qualification_id) REFERENCES qua_portal_submission(qualification_id),
 CONSTRAINT fk_portal_withdrawal_account FOREIGN KEY(account_id) REFERENCES iam_portal_account(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
