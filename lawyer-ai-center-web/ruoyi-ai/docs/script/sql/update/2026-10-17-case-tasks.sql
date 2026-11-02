-- Extends the existing lawyer_case. Apply after lawyer-cases.sql; no replacement case table.
CREATE TABLE IF NOT EXISTS lawyer_case_task (
 id BIGINT NOT NULL PRIMARY KEY,
 tenant_id VARCHAR(20) NOT NULL,
 user_id BIGINT NOT NULL,
 case_id BIGINT NOT NULL,
 title VARCHAR(200) NOT NULL,
 status VARCHAR(10) NOT NULL DEFAULT 'todo',
 due_at DATETIME NULL,
 sort_order INT NOT NULL DEFAULT 0,
 work_minutes BIGINT NOT NULL DEFAULT 0,
 created_at DATETIME NOT NULL,
 updated_at DATETIME NOT NULL,
 KEY idx_case_owner (tenant_id,user_id,case_id,sort_order,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS lawyer_case_checklist_item (
 id BIGINT NOT NULL PRIMARY KEY,
 task_id BIGINT NOT NULL,
 content VARCHAR(1000) NOT NULL,
 checked TINYINT NOT NULL DEFAULT 0,
 checked_at DATETIME NULL,
 created_at DATETIME NOT NULL,
 KEY idx_task_items (task_id,created_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- Notes and request keys are kept separately from the accumulated task minutes.
CREATE TABLE IF NOT EXISTS lawyer_case_task_time (
 id BIGINT NOT NULL PRIMARY KEY,
 task_id BIGINT NOT NULL,
 minutes INT NOT NULL,
 note VARCHAR(1000) NULL,
 request_id VARCHAR(64) NOT NULL,
 created_at DATETIME NOT NULL,
 UNIQUE KEY uk_task_request (task_id,request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
