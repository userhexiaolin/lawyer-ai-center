CREATE TABLE IF NOT EXISTS lawyer_case (
 id BIGINT NOT NULL PRIMARY KEY,
 tenant_id VARCHAR(20) NOT NULL,
 user_id BIGINT NOT NULL,
 title VARCHAR(200) NOT NULL,
 case_type VARCHAR(100) NULL,
 client_name VARCHAR(200) NULL,
 opposite_party VARCHAR(200) NULL,
 status VARCHAR(20) NOT NULL DEFAULT '进行中',
 court VARCHAR(200) NULL,
 filing_date DATE NULL,
 next_hearing_date DATE NULL,
 limitation_date DATE NULL,
 description TEXT NULL,
 deleted TINYINT NOT NULL DEFAULT 0,
 create_dept BIGINT NULL, create_by BIGINT NULL, create_time DATETIME NULL,
 update_by BIGINT NULL, update_time DATETIME NULL,
 KEY idx_owner_status (tenant_id,user_id,deleted,status),
 KEY idx_owner_hearing (tenant_id,user_id,deleted,next_hearing_date),
 KEY idx_owner_limitation (tenant_id,user_id,deleted,limitation_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- Apply this ALTER once before deploying the matching backend.
ALTER TABLE chat_session ADD COLUMN case_id BIGINT NULL, ADD KEY idx_case_user (case_id,user_id);
CREATE TABLE IF NOT EXISTS lawyer_case_file (
 tenant_id VARCHAR(20) NOT NULL, user_id BIGINT NOT NULL,
 case_id BIGINT NOT NULL, oss_id BIGINT NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY (tenant_id,user_id,case_id,oss_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
