ALTER TABLE sys_audit_event
    ADD COLUMN support_session_ref VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin
        GENERATED ALWAYS AS (
            CASE WHEN tenant_id = 0 AND object_type = 'SUPPLIER'
                      AND action IN ('SUPPORT_SUPPLIER_READ', 'SUPPORT_SUPPLIER_READ_DENIED')
                      AND CHAR_LENGTH(JSON_UNQUOTE(JSON_EXTRACT(after_summary, '$.sessionId'))) BETWEEN 1 AND 19
                 THEN JSON_UNQUOTE(JSON_EXTRACT(after_summary, '$.sessionId'))
                 ELSE NULL END
        ) VIRTUAL,
    ADD INDEX idx_audit_support_session (tenant_id, operator_id, support_session_ref, occurred_at, id);
