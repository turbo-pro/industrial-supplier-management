CREATE INDEX idx_portal_session_retention ON iam_portal_session(expires_at,tenant_id);
CREATE INDEX idx_portal_session_tenant_retention ON iam_portal_session(tenant_id,expires_at,id);
