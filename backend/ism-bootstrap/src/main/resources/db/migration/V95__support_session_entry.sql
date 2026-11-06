ALTER TABLE plt_support_session
    ADD COLUMN entry_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN browser_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN entry_expires_at DATETIME(3) NULL,
    ADD COLUMN activated_at DATETIME(3) NULL;
