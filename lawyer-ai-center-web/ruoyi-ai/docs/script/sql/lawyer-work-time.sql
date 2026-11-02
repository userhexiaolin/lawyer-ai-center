CREATE TABLE IF NOT EXISTS lawyer_work_time (
 id BIGINT NOT NULL PRIMARY KEY,
 tenant_id VARCHAR(20) NOT NULL,
 user_id BIGINT NOT NULL,
 case_id BIGINT NULL,
 session_id BIGINT NULL,
 start_time DATETIME(3) NOT NULL,
 end_time DATETIME(3) NULL,
 duration_seconds BIGINT NOT NULL DEFAULT 0,
 duration_minutes DECIMAL(14,3) NOT NULL DEFAULT 0,
 description VARCHAR(500) NULL,
 created_at DATETIME(3) NOT NULL,
 active_slot TINYINT NULL DEFAULT 1,
 UNIQUE KEY one_active_timer (tenant_id,user_id,active_slot),
 KEY owner_history (tenant_id,user_id,start_time),
 KEY owner_case (tenant_id,user_id,case_id,start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
