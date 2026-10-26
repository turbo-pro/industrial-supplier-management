ALTER TABLE qua_portal_submission
 ADD COLUMN correction_due_date DATE NULL,
 ADD COLUMN correction_requirements JSON NULL;
ALTER TABLE qua_portal_correction
 ADD COLUMN source_due_date DATE NULL,
 ADD COLUMN source_requirements JSON NULL,
 ADD COLUMN correction_responses JSON NULL,
 ADD COLUMN late_flag BOOLEAN NOT NULL DEFAULT FALSE;
