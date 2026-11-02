-- Fresh-install audit: these four @TableName entities were absent from the old dump.
-- Fields follow the current Java entities and their inherited BaseEntity/TenantEntity.
-- Additive only. Existing installations must compare existing definitions before upgrading.
-- No users, passwords, graph data, or configuration secrets are seeded here.
CREATE TABLE IF NOT EXISTS chat_config (
 id BIGINT NOT NULL PRIMARY KEY,
 tenant_id VARCHAR(20) NOT NULL DEFAULT '000000',
 category VARCHAR(100) NULL, config_name VARCHAR(200) NULL,
 config_value MEDIUMTEXT NULL, config_dict VARCHAR(500) NULL, remark VARCHAR(500) NULL,
 version BIGINT NOT NULL DEFAULT 0, del_flag CHAR(1) NOT NULL DEFAULT '0', update_ip VARCHAR(128) NULL,
 create_dept BIGINT NULL, create_by BIGINT NULL, create_time DATETIME NULL,
 update_by BIGINT NULL, update_time DATETIME NULL,
 KEY idx_chat_config_tenant(tenant_id,category,del_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS knowledge_graph_instance (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL DEFAULT 0,
 graph_uuid VARCHAR(64) NULL, knowledge_id VARCHAR(64) NULL, graph_name VARCHAR(200) NULL,
 graph_status BIGINT NULL, node_count BIGINT NULL, relationship_count BIGINT NULL,
 config MEDIUMTEXT NULL, model_name VARCHAR(255) NULL, entity_types TEXT NULL, relation_types TEXT NULL,
 error_message TEXT NULL, remark VARCHAR(500) NULL, del_flag CHAR(1) NOT NULL DEFAULT '0',
 create_dept BIGINT NULL, create_by BIGINT NULL, create_time DATETIME NULL,
 update_by BIGINT NULL, update_time DATETIME NULL,
 KEY idx_graph_uuid(tenant_id,graph_uuid), KEY idx_graph_knowledge(tenant_id,knowledge_id,del_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS knowledge_graph_segment (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL DEFAULT 0,
 uuid VARCHAR(64) NULL, kb_uuid VARCHAR(64) NULL, kb_item_uuid VARCHAR(64) NULL, doc_uuid VARCHAR(64) NULL,
 segment_text MEDIUMTEXT NULL, chunk_index BIGINT NULL, total_chunks BIGINT NULL,
 extraction_status BIGINT NULL, entity_count BIGINT NULL, relation_count BIGINT NULL,
 token_used BIGINT NULL, error_message TEXT NULL, user_id BIGINT NULL, remark VARCHAR(500) NULL,
 create_dept BIGINT NULL, create_by BIGINT NULL, create_time DATETIME NULL,
 update_by BIGINT NULL, update_time DATETIME NULL,
 KEY idx_graph_segment(tenant_id,kb_uuid,doc_uuid), KEY idx_graph_segment_uuid(tenant_id,uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Compatibility entity used by workflow context. This is NOT the sys_user login table.
CREATE TABLE IF NOT EXISTS adi_user (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 tenant_id VARCHAR(20) NOT NULL DEFAULT '000000',
 name VARCHAR(200) NULL, email VARCHAR(255) NULL, password VARCHAR(255) NULL, uuid VARCHAR(64) NULL,
 understand_context_msg_pair_num INT NULL, quota_by_token_daily INT NULL, quota_by_token_monthly INT NULL,
 quota_by_request_daily INT NULL, quota_by_request_monthly INT NULL,
 quota_by_image_daily INT NULL, quota_by_image_monthly INT NULL,
 user_status VARCHAR(32) NULL, active_time DATETIME NULL, is_admin TINYINT(1) NOT NULL DEFAULT 0,
 create_time DATETIME NULL, update_time DATETIME NULL, is_deleted TINYINT(1) NOT NULL DEFAULT 0,
 KEY idx_adi_user_uuid(tenant_id,uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
