-- Task87 additive extension. Existing cases/files/pages/jobs remain the only truth source.
CREATE TABLE IF NOT EXISTS lawyer_case_source_anchor (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id VARCHAR(20) NOT NULL, case_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
 file_version_id BIGINT NOT NULL, page_id BIGINT NOT NULL, page_index INT NOT NULL, kind VARCHAR(30) NOT NULL,
 fragment_start INT NULL, fragment_end INT NULL, fragment_text TEXT NOT NULL, page_hash CHAR(64) NOT NULL,
 file_hash CHAR(64) NOT NULL, image_hash CHAR(64) NULL, region_hash CHAR(64) NULL, geometry_json TEXT NULL,
 table_row INT NULL, table_column INT NULL, pinned_history TINYINT NOT NULL DEFAULT 0,
 fingerprint CHAR(64) NOT NULL, create_by BIGINT NOT NULL, create_time DATETIME NOT NULL,
 UNIQUE KEY uk_anchor_fingerprint(tenant_id,case_id,fingerprint), KEY idx_anchor_page(tenant_id,case_id,file_version_id,page_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS lawyer_case_claim (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id VARCHAR(20) NOT NULL, case_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
 kind VARCHAR(30) NOT NULL, claim_text TEXT NOT NULL, original_text TEXT NOT NULL, values_json TEXT NOT NULL,
 risk VARCHAR(20) NOT NULL, review_status VARCHAR(30) NOT NULL DEFAULT 'pending_review',
 source_status VARCHAR(30) NOT NULL DEFAULT 'source_unverified', source_errors TEXT NOT NULL,
 origin_key CHAR(64) NULL, last_run_id BIGINT NULL, edited TINYINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 1,
 reviewer_id BIGINT NULL, reviewed_at DATETIME NULL, create_by BIGINT NOT NULL, create_time DATETIME NOT NULL,
 update_by BIGINT NULL, update_time DATETIME NOT NULL,
 UNIQUE KEY uk_claim_origin(tenant_id,case_id,origin_key), KEY idx_claim_review(tenant_id,case_id,review_status,kind)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS lawyer_case_claim_source (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id VARCHAR(20) NOT NULL, case_id BIGINT NOT NULL, claim_id BIGINT NOT NULL,
 anchor_id BIGINT NOT NULL, relation VARCHAR(20) NOT NULL, create_by BIGINT NOT NULL, create_time DATETIME NOT NULL,
 UNIQUE KEY uk_claim_anchor(claim_id,anchor_id,relation), KEY idx_claim_source(tenant_id,case_id,anchor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS lawyer_case_claim_suggestion (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id VARCHAR(20) NOT NULL, case_id BIGINT NOT NULL, claim_id BIGINT NOT NULL,
 run_id BIGINT NOT NULL, proposed_json TEXT NOT NULL, state VARCHAR(20) NOT NULL DEFAULT 'pending',
 operator_id BIGINT NULL, reason VARCHAR(500) NULL, version INT NOT NULL DEFAULT 1, create_time DATETIME NOT NULL,
 update_time DATETIME NOT NULL, KEY idx_claim_suggestion(tenant_id,case_id,claim_id,state)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS lawyer_case_review_change (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id VARCHAR(20) NOT NULL, case_id BIGINT NOT NULL,
 object_kind VARCHAR(30) NOT NULL, object_id BIGINT NOT NULL, action VARCHAR(30) NOT NULL,
 before_json MEDIUMTEXT NOT NULL, after_json MEDIUMTEXT NOT NULL, reason VARCHAR(500) NOT NULL,
 operator_id BIGINT NOT NULL, create_time DATETIME NOT NULL,
 KEY idx_review_history(tenant_id,case_id,object_kind,object_id,create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS lawyer_case_ai_run (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id VARCHAR(20) NOT NULL, case_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
 job_id BIGINT NOT NULL, purpose VARCHAR(30) NOT NULL, model_id BIGINT NOT NULL, model_name VARCHAR(200) NOT NULL,
 provider VARCHAR(100) NOT NULL, endpoint VARCHAR(500) NOT NULL, region VARCHAR(100) NOT NULL,
 policy_version VARCHAR(100) NOT NULL, prompt_version VARCHAR(100) NOT NULL,
 scope_json MEDIUMTEXT NOT NULL, request_hash CHAR(64) NOT NULL, state VARCHAR(30) NOT NULL DEFAULT 'queued',
 readable_pages INT NOT NULL, total_pages INT NULL, chunk_count INT NOT NULL DEFAULT 0,
 successful_chunks INT NOT NULL DEFAULT 0, failed_chunks INT NOT NULL DEFAULT 0,
 reserved_tokens BIGINT NOT NULL DEFAULT 0, actual_tokens BIGINT NULL,
 error_code VARCHAR(60) NULL, create_by BIGINT NOT NULL, create_time DATETIME NOT NULL, update_time DATETIME NOT NULL,
 UNIQUE KEY uk_ai_job(job_id), KEY idx_ai_quota(tenant_id,user_id,create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS lawyer_case_ai_chunk (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id VARCHAR(20) NOT NULL, case_id BIGINT NOT NULL, run_id BIGINT NOT NULL,
 chunk_index INT NOT NULL, file_version_id BIGINT NOT NULL, page_index INT NOT NULL,
 anchors_json TEXT NOT NULL, state VARCHAR(30) NOT NULL DEFAULT 'queued', reserved_tokens BIGINT NOT NULL DEFAULT 0,
 input_tokens BIGINT NULL, output_tokens BIGINT NULL, response_json MEDIUMTEXT NULL,
 rejection_json TEXT NULL, error_code VARCHAR(60) NULL, create_time DATETIME NOT NULL, update_time DATETIME NOT NULL,
 UNIQUE KEY uk_ai_chunk(run_id,chunk_index), KEY idx_ai_chunk(tenant_id,case_id,run_id,state)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS lawyer_case_ai_budget (
 tenant_id VARCHAR(20) NOT NULL, user_id BIGINT NOT NULL, period VARCHAR(7) NOT NULL,
 tokens_charged BIGINT NOT NULL DEFAULT 0, pages_charged INT NOT NULL DEFAULT 0,
 update_time DATETIME NOT NULL, PRIMARY KEY(tenant_id,user_id,period)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS lawyer_case_ai_attempt (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id VARCHAR(20) NOT NULL, case_id BIGINT NOT NULL,
 run_id BIGINT NOT NULL, chunk_id BIGINT NOT NULL, period VARCHAR(7) NOT NULL,
 state VARCHAR(30) NOT NULL, reserved_tokens BIGINT NOT NULL,
 input_tokens BIGINT NULL, output_tokens BIGINT NULL, response_hash CHAR(64) NULL,
 create_time DATETIME NOT NULL, update_time DATETIME NOT NULL,
 KEY idx_ai_attempt(tenant_id,case_id,run_id,chunk_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
