-- Tenant-scoped suggestions: no complete template text or API credentials are retained.
CREATE TABLE IF NOT EXISTS legal_doc_template_type_decision (
 tenant_id VARCHAR(20) NOT NULL,
 fingerprint CHAR(64) NOT NULL,
 source_digest CHAR(64) NOT NULL,
 document_type VARCHAR(100) NULL,
 method VARCHAR(16) NOT NULL,
 evidence VARCHAR(200) NOT NULL DEFAULT '',
 model_id BIGINT NOT NULL,
 model_name VARCHAR(255) NOT NULL,
 policy_version VARCHAR(50) NOT NULL,
 created_by BIGINT NOT NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY (tenant_id,fingerprint)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
