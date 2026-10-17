-- A shared data resource can be consumed by more than one business module.
-- Unknown resources fail closed; historical role configurations remain untouched.
CREATE TABLE sys_data_scope_resource_module (
    resource_code VARCHAR(100) NOT NULL,
    module_code VARCHAR(64) NOT NULL,
    PRIMARY KEY (resource_code, module_code)
);

INSERT INTO sys_data_scope_resource_module(resource_code,module_code) VALUES
('iam:organization','SYSTEM'),('iam:user','SYSTEM'),
('resource:file','COMMON'),('print:template','COMMON'),
('supplier:master','SUPPLIER'),('supplier:master','RESTRICTION'),
('supplier:admission','QUALIFICATION'),('supplier:qualification','QUALIFICATION'),
('contract','PROJECT'),('project','PROJECT'),
('resource:person','RESOURCE'),('resource:asset','RESOURCE'),
('safety:issue','SAFETY'),('safety:credential','SAFETY'),('safety:attendance','SAFETY'),
('quality:ncr','QUALITY'),('performance:evaluation','PERFORMANCE');
