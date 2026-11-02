-- Task 85. Additive migration. Run the guarded DossierMigration tool, never the whole initial schema.
-- Preserve lawyer_case_file's original composite PK and all legacy associations.
ALTER TABLE lawyer_case_file ADD COLUMN id BIGINT NULL,
 ADD COLUMN display_name VARCHAR(255) NULL, ADD COLUMN category VARCHAR(40) NOT NULL DEFAULT 'unclassified',
 ADD COLUMN current_version_id BIGINT NULL, ADD UNIQUE KEY uk_case_file_id(id);

CREATE TABLE IF NOT EXISTS lawyer_case_upload (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id VARCHAR(20) NOT NULL, case_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
 material_id BIGINT NULL, file_name VARCHAR(255) NOT NULL, file_bytes BIGINT NOT NULL, sha256 CHAR(64) NOT NULL,
 idempotency_key VARCHAR(64) NOT NULL, keep_duplicate TINYINT NOT NULL DEFAULT 0,
 state VARCHAR(20) NOT NULL DEFAULT 'initialized', oss_id BIGINT NULL, file_version_id BIGINT NULL,
 mime VARCHAR(100) NULL, lease_until DATETIME NULL, create_time DATETIME NOT NULL, update_time DATETIME NOT NULL,
 UNIQUE KEY uk_upload_request(tenant_id,case_id,idempotency_key), KEY idx_upload_expiry(state,update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS lawyer_case_file_version (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id VARCHAR(20) NOT NULL, case_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
 material_id BIGINT NOT NULL, oss_id BIGINT NOT NULL, version_no INT NOT NULL, file_name VARCHAR(255) NOT NULL,
 sha256 CHAR(64) NOT NULL, file_bytes BIGINT NOT NULL, mime VARCHAR(100) NOT NULL,
 upload_status VARCHAR(20) NOT NULL DEFAULT 'uploaded', parse_status VARCHAR(20) NOT NULL DEFAULT 'not_started',
 page_count INT NULL, parsed_pages INT NOT NULL DEFAULT 0, failed_pages INT NOT NULL DEFAULT 0,
 parser_version VARCHAR(80) NOT NULL, last_job_id BIGINT NULL, error_code VARCHAR(60) NULL, error_message VARCHAR(300) NULL,
 create_by BIGINT NOT NULL, create_time DATETIME NOT NULL, update_time DATETIME NOT NULL,
 UNIQUE KEY uk_material_version(material_id,version_no), KEY idx_version_case(tenant_id,case_id),
 KEY idx_version_hash(tenant_id,case_id,sha256)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS lawyer_case_file_page (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id VARCHAR(20) NOT NULL, case_id BIGINT NOT NULL,
 file_version_id BIGINT NOT NULL, page_index INT NOT NULL, page_label VARCHAR(40) NULL,
 extract_status VARCHAR(20) NOT NULL, ocr_status VARCHAR(20) NOT NULL, preview_status VARCHAR(20) NOT NULL,
 extraction_method VARCHAR(40) NOT NULL, text_content MEDIUMTEXT NULL, char_count INT NOT NULL DEFAULT 0,
 text_hash CHAR(64) NULL, empty_page_confirmed TINYINT NOT NULL DEFAULT 0,
 error_code VARCHAR(60) NULL, error_message VARCHAR(300) NULL, parser_version VARCHAR(80) NOT NULL,
 last_job_id BIGINT NOT NULL, version INT NOT NULL DEFAULT 1, update_time DATETIME NOT NULL,
 UNIQUE KEY uk_version_page(file_version_id,page_index), KEY idx_page_case(tenant_id,case_id,file_version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS lawyer_case_parse_job (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id VARCHAR(20) NOT NULL, case_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
 file_version_id BIGINT NOT NULL, job_type VARCHAR(30) NOT NULL DEFAULT 'parse', idempotency_key VARCHAR(64) NOT NULL,
 requested_pages TEXT NOT NULL, state VARCHAR(20) NOT NULL DEFAULT 'queued', progress_done INT NOT NULL DEFAULT 0,
 progress_total INT NULL, attempts INT NOT NULL DEFAULT 0, lease_token VARCHAR(36) NULL, lease_until DATETIME NULL,
 error_code VARCHAR(60) NULL, error_message VARCHAR(300) NULL, create_time DATETIME NOT NULL, update_time DATETIME NOT NULL,
 active_version_id BIGINT GENERATED ALWAYS AS (IF(state IN ('queued','running'), file_version_id, NULL)) STORED,
 UNIQUE KEY uk_job_request(tenant_id,case_id,job_type,idempotency_key), UNIQUE KEY uk_active_version(active_version_id),
 KEY idx_job_queue(state,lease_until,create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS lawyer_case_parse_log (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id VARCHAR(20) NOT NULL, case_id BIGINT NOT NULL,
 file_version_id BIGINT NOT NULL, job_id BIGINT NULL, page_index INT NULL, action VARCHAR(40) NOT NULL,
 before_status VARCHAR(30) NULL, after_status VARCHAR(30) NOT NULL, error_code VARCHAR(60) NULL,
 operator_id BIGINT NOT NULL, create_time DATETIME NOT NULL,
 KEY idx_parse_log(tenant_id,case_id,file_version_id,create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
