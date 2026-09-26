INSERT INTO cfg_setting_definition(setting_key,setting_name,value_type,default_value,status)
VALUES('restriction.watchPeriodDays','观察名单最短观察天数','INTEGER','7','ACTIVE');

ALTER TABLE sup_lift_application
 ADD COLUMN observation_seconds BIGINT NOT NULL DEFAULT 604800,
 ADD CONSTRAINT chk_lift_observation_seconds CHECK(observation_seconds>=0);

-- Existing applications retain at least the original seven-day default.
UPDATE sup_lift_application a
 JOIN sup_blacklist_case b ON b.id=a.case_id AND b.tenant_id=a.tenant_id
 SET a.observation_seconds=0 WHERE b.restriction_type<>'WATCH';
