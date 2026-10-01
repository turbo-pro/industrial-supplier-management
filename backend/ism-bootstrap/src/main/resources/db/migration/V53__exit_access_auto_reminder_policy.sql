INSERT INTO cfg_setting_definition(setting_key,setting_name,value_type,default_value,status) VALUES
 ('exit.accessAutoReminderEnabled','外部访问核查逾期自动催办','INTEGER','0','ACTIVE'),
 ('exit.accessAutoReminderIntervalHours','外部访问核查自动催办间隔（小时）','INTEGER','24','ACTIVE');

CREATE INDEX idx_exit_access_auto_reminder ON sup_exit_access_recovery_task(tenant_id,due_date,last_reminded_at,id);
