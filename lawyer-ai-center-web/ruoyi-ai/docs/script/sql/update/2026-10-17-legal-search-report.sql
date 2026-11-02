-- Personal search reports. No data migration or external case-library dependency.
CREATE TABLE IF NOT EXISTS legal_search_report (
  id BIGINT NOT NULL PRIMARY KEY,
  tenant_id VARCHAR(20) NOT NULL,
  user_id BIGINT NOT NULL,
  title VARCHAR(200) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'draft',
  background MEDIUMTEXT NULL,
  conclusion MEDIUMTEXT NULL,
  file_oss_id BIGINT NULL,
  generated_at DATETIME NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  del_flag TINYINT NOT NULL DEFAULT 0,
  KEY idx_report_owner (tenant_id, user_id, del_flag, updated_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS legal_search_report_item (
  id BIGINT NOT NULL PRIMARY KEY,
  report_id BIGINT NOT NULL,
  item_type VARCHAR(24) NOT NULL,
  ref_id VARCHAR(200) NULL,
  section_key VARCHAR(100) NULL,
  snapshot_title VARCHAR(500) NOT NULL,
  snapshot_content MEDIUMTEXT NOT NULL,
  law_status VARCHAR(16) NULL,
  law_name VARCHAR(300) NULL,
  article_no VARCHAR(100) NULL,
  source_url VARCHAR(2000) NULL,
  -- SHA-256 of the normalized law name + article number; NULL for other items.
  law_key CHAR(64) NULL,
  sort_order INT NOT NULL,
  created_at DATETIME NOT NULL,
  UNIQUE KEY uk_report_law (report_id, law_key),
  KEY idx_report_item_order (report_id, sort_order, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
