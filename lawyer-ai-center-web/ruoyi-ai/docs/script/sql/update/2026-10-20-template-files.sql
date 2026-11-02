-- Immutable source files and export layout. Existing templates and drafts are preserved.
CREATE TABLE IF NOT EXISTS legal_doc_template_file (
 version_id char(36) NOT NULL PRIMARY KEY,
 tenant_id varchar(20) NOT NULL,
 uploaded_by bigint NOT NULL,
 template_id bigint NULL,
 filename varchar(255) NOT NULL,
 source_format varchar(12) NOT NULL,
 source_bytes longblob NOT NULL,
 docx_bytes longblob NOT NULL,
 blocks_json mediumtext NOT NULL,
 created_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 bound_at datetime(6) NULL,
 KEY idx_template_version (tenant_id,template_id,bound_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
