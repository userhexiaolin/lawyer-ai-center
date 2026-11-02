-- Additive migration. Activation intent is independent of reviewed field readiness.
CREATE TABLE IF NOT EXISTS legal_doc_template_field_task (
 tenant_id VARCHAR(20) NOT NULL,
 template_id BIGINT NOT NULL,
 task_id CHAR(36) NOT NULL,
 uploaded_by BIGINT NOT NULL,
 source_version CHAR(36) NOT NULL,
 expected_revision CHAR(64) NOT NULL,
 document_type VARCHAR(100) NOT NULL,
 state VARCHAR(16) NOT NULL,
 phase VARCHAR(100) NOT NULL,
 warning VARCHAR(1000) NOT NULL DEFAULT '',
 result_version CHAR(36) NULL,
 created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 expires_at DATETIME(6) NOT NULL,
 PRIMARY KEY (tenant_id,template_id),
 UNIQUE KEY uk_template_field_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
