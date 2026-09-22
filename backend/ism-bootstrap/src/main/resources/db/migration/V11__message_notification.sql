CREATE TABLE msg_template (
 id BIGINT NOT NULL,tenant_id BIGINT NOT NULL,template_code VARCHAR(64) NOT NULL,template_name VARCHAR(100) NOT NULL,
 channel VARCHAR(24) NOT NULL,title_template VARCHAR(200) NOT NULL,content_template TEXT NOT NULL,variable_schema JSON NOT NULL,
 status VARCHAR(24) NOT NULL,version INT NOT NULL DEFAULT 0,created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),PRIMARY KEY(id),
 UNIQUE KEY uk_msg_template(tenant_id,template_code,channel),CONSTRAINT fk_msg_template_tenant FOREIGN KEY(tenant_id) REFERENCES iam_tenant(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE msg_delivery (
 id BIGINT NOT NULL,tenant_id BIGINT NOT NULL,template_id BIGINT NOT NULL,channel VARCHAR(24) NOT NULL,recipient_id BIGINT NOT NULL,
 title VARCHAR(200) NOT NULL,content TEXT NOT NULL,business_type VARCHAR(64) NULL,business_id BIGINT NULL,status VARCHAR(24) NOT NULL,
 failure_reason VARCHAR(500) NULL,sent_at DATETIME(3) NULL,created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),PRIMARY KEY(id),
 KEY idx_msg_delivery_recipient(tenant_id,recipient_id,created_at),CONSTRAINT fk_msg_delivery_tenant FOREIGN KEY(tenant_id) REFERENCES iam_tenant(id),
 CONSTRAINT fk_msg_delivery_template FOREIGN KEY(template_id) REFERENCES msg_template(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE msg_inbox (
 id BIGINT NOT NULL,tenant_id BIGINT NOT NULL,delivery_id BIGINT NOT NULL,recipient_id BIGINT NOT NULL,read_at DATETIME(3) NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),PRIMARY KEY(id),UNIQUE KEY uk_msg_inbox_delivery(tenant_id,delivery_id),
 KEY idx_msg_inbox_unread(tenant_id,recipient_id,read_at,created_at),CONSTRAINT fk_msg_inbox_tenant FOREIGN KEY(tenant_id) REFERENCES iam_tenant(id),
 CONSTRAINT fk_msg_inbox_delivery FOREIGN KEY(delivery_id) REFERENCES msg_delivery(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
INSERT INTO sys_permission(id,permission_code,permission_name,permission_type,status) VALUES
 (5101,'message:template:view','查看消息模板','ACTION','ACTIVE'),(5102,'message:template:manage','管理消息模板','ACTION','ACTIVE'),
 (5103,'message:send','发送消息','ACTION','ACTIVE'),(5104,'message:inbox:view','查看个人消息','ACTION','ACTIVE');
INSERT INTO sys_menu(id,parent_id,module_code,menu_code,menu_name,route_path,component_key,icon,sort_order,status) VALUES
 (5100,NULL,'MESSAGE','MESSAGE_CENTER','消息中心','/messages','Layout','bell',710,'ACTIVE'),
 (5101,5100,'MESSAGE','MY_INBOX','我的消息','/messages/inbox','InboxPage','message',10,'ACTIVE'),
 (5102,5100,'MESSAGE','MESSAGE_TEMPLATE','消息模板','/messages/templates','MessageTemplatePage','tickets',20,'ACTIVE');
