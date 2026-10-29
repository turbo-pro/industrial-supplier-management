-- Existing tenants remain explicitly unclassified; do not rewrite organization or business history.
ALTER TABLE plt_tenant
    ADD COLUMN operation_mode VARCHAR(20) NULL,
    ADD COLUMN site_label VARCHAR(20) NULL,
    ADD COLUMN default_site_id BIGINT NULL;
