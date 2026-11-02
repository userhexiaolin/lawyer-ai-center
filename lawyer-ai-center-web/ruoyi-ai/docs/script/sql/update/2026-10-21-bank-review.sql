-- Task 86. Additive, register with checksum in stage4_schema_migration after backup/audit.
CREATE TABLE IF NOT EXISTS lawyer_case_bank_run (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id VARCHAR(20) NOT NULL, case_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
 file_version_id BIGINT NOT NULL, job_id BIGINT NOT NULL, parser_version VARCHAR(80) NOT NULL,
 source_hash CHAR(64) NOT NULL, state VARCHAR(20) NOT NULL, readable_pages INT NOT NULL DEFAULT 0,
 total_pages INT NULL, row_count INT NOT NULL DEFAULT 0, unparsed_count INT NOT NULL DEFAULT 0,
 error_code VARCHAR(60) NULL, error_message VARCHAR(300) NULL,
 create_by BIGINT NOT NULL, create_time DATETIME NOT NULL, update_time DATETIME NOT NULL,
 UNIQUE KEY uk_bank_job(job_id), KEY idx_bank_run(tenant_id,case_id,file_version_id,create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS lawyer_case_bank_export (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id VARCHAR(20) NOT NULL, case_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
 job_id BIGINT NOT NULL, format VARCHAR(8) NOT NULL, request_hash CHAR(64) NOT NULL, snapshot_json MEDIUMTEXT NOT NULL,
 snapshot_hash CHAR(64) NOT NULL, row_count INT NOT NULL, pending_count INT NOT NULL,
 oss_id BIGINT NULL, file_hash CHAR(64) NULL, create_time DATETIME NOT NULL,
 UNIQUE KEY uk_bank_export_job(job_id), KEY idx_bank_export_case(tenant_id,case_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS lawyer_case_bank_page (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id VARCHAR(20) NOT NULL, case_id BIGINT NOT NULL,
 run_id BIGINT NOT NULL, file_version_id BIGINT NOT NULL, page_index INT NOT NULL,
 row_count INT NOT NULL DEFAULT 0, unparsed_json MEDIUMTEXT NOT NULL, warnings_json TEXT NOT NULL,
 UNIQUE KEY uk_bank_run_page(run_id,page_index), KEY idx_bank_page(tenant_id,case_id,file_version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS lawyer_case_bank_row (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id VARCHAR(20) NOT NULL, case_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
 file_version_id BIGINT NOT NULL, page_id BIGINT NOT NULL, page_index INT NOT NULL, table_row INT NOT NULL,
 fragment_start INT NOT NULL, fragment_end INT NOT NULL, source_variant INT NOT NULL DEFAULT 0, fragment_text TEXT NOT NULL, page_hash CHAR(64) NOT NULL,
 source_hash CHAR(64) NOT NULL, last_run_id BIGINT NOT NULL, suggestion_json TEXT NULL,
 trade_time_raw VARCHAR(100) NULL, trade_at VARCHAR(30) NULL, amount_raw VARCHAR(100) NULL,
 amount DECIMAL(20,2) NULL, direction VARCHAR(20) NOT NULL, currency VARCHAR(8) NULL,
 balance_raw VARCHAR(100) NULL, balance DECIMAL(20,2) NULL,
 account_raw VARCHAR(160) NULL, counter_account_raw VARCHAR(160) NULL, counterparty_raw VARCHAR(200) NULL,
 transaction_ref VARCHAR(200) NULL, summary VARCHAR(1000) NULL, reasons_json TEXT NOT NULL,
 anomaly_json TEXT NOT NULL, review_status VARCHAR(30) NOT NULL DEFAULT 'pending_review',
 included TINYINT NOT NULL DEFAULT 1, edited TINYINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 1,
 create_by BIGINT NOT NULL, create_time DATETIME NOT NULL, update_by BIGINT NULL, update_time DATETIME NOT NULL,
 UNIQUE KEY uk_bank_source(file_version_id,page_index,fragment_start,source_variant), KEY idx_bank_case(tenant_id,case_id,review_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS lawyer_case_bank_change (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id VARCHAR(20) NOT NULL, case_id BIGINT NOT NULL,
 row_id BIGINT NOT NULL, action VARCHAR(40) NOT NULL, before_json MEDIUMTEXT NOT NULL, after_json MEDIUMTEXT NOT NULL,
 reason VARCHAR(500) NOT NULL, operator_id BIGINT NOT NULL, create_time DATETIME NOT NULL,
 KEY idx_bank_change(tenant_id,case_id,row_id,create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS lawyer_case_bank_duplicate (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id VARCHAR(20) NOT NULL, case_id BIGINT NOT NULL,
 left_row_id BIGINT NOT NULL, right_row_id BIGINT NOT NULL, decision VARCHAR(30) NOT NULL DEFAULT 'pending',
 reason VARCHAR(500) NOT NULL, operator_id BIGINT NULL, version INT NOT NULL DEFAULT 1, update_time DATETIME NOT NULL,
 UNIQUE KEY uk_bank_pair(left_row_id,right_row_id), KEY idx_bank_duplicate(tenant_id,case_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
