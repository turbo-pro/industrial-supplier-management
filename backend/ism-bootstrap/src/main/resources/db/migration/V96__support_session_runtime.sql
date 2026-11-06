ALTER TABLE plt_support_session
    ADD COLUMN access_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN revoked_at DATETIME(3) NULL;
