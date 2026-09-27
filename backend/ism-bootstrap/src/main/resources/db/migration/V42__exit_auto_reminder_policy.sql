INSERT INTO cfg_setting_definition(setting_key,setting_name,value_type,default_value,status) VALUES
 ('exit.autoReminderEnabled','退出逾期自动催办','INTEGER','0','ACTIVE'),
 ('exit.autoReminderIntervalHours','退出自动催办间隔（小时）','INTEGER','24','ACTIVE');

CREATE INDEX idx_exit_entity_auto_reminder ON sup_exit_entity(tenant_id,state,due_date,last_reminded_at,id);
