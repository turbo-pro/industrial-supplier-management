ALTER TABLE qua_portal_submission
 ADD COLUMN public_review_comment VARCHAR(1000) NULL,
 ADD COLUMN public_reviewed_at DATETIME(3) NULL;
