-- Task89 additive events. Do not modify registered migrations or backfill guessed dates.
CREATE TABLE IF NOT EXISTS lawyer_case_event (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id VARCHAR(20) NOT NULL, case_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
 title VARCHAR(300) NOT NULL, summary TEXT NOT NULL, event_type VARCHAR(50) NOT NULL,
 participants_json TEXT NOT NULL, date_kind VARCHAR(20) NOT NULL, date_raw VARCHAR(500) NOT NULL,
 date_start VARCHAR(10) NULL, date_end VARCHAR(10) NULL, timezone VARCHAR(100) NULL,
 review_status VARCHAR(30) NOT NULL DEFAULT 'pending_review', source_status VARCHAR(30) NOT NULL,
 source_errors TEXT NOT NULL, origin VARCHAR(30) NOT NULL, origin_claim_id BIGINT NULL,
 group_label VARCHAR(100) NOT NULL, sort_order INT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 1,
 reviewer_id BIGINT NULL, reviewed_at DATETIME NULL, create_by BIGINT NOT NULL, create_time DATETIME NOT NULL,
 update_by BIGINT NULL, update_time DATETIME NOT NULL,
 KEY idx_event_case(tenant_id,case_id,review_status,date_kind), KEY idx_event_claim(tenant_id,case_id,origin_claim_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS lawyer_case_event_source (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id VARCHAR(20) NOT NULL, case_id BIGINT NOT NULL,
 event_id BIGINT NOT NULL, anchor_id BIGINT NOT NULL, relation VARCHAR(20) NOT NULL,
 create_by BIGINT NOT NULL, create_time DATETIME NOT NULL,
 UNIQUE KEY uk_event_source(event_id,anchor_id,relation), KEY idx_event_source(tenant_id,case_id,anchor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS lawyer_case_event_precedence (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id VARCHAR(20) NOT NULL, case_id BIGINT NOT NULL,
 before_event_id BIGINT NOT NULL, after_event_id BIGINT NOT NULL, state VARCHAR(20) NOT NULL DEFAULT 'draft',
 version INT NOT NULL DEFAULT 1, reason VARCHAR(500) NOT NULL, create_by BIGINT NOT NULL, create_time DATETIME NOT NULL,
 update_by BIGINT NULL, update_time DATETIME NOT NULL,
 KEY idx_event_precedence(tenant_id,case_id,state,before_event_id,after_event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS lawyer_case_timeline_revision (
 tenant_id VARCHAR(20) NOT NULL, case_id BIGINT NOT NULL, revision BIGINT NOT NULL DEFAULT 0,
 update_by BIGINT NOT NULL, update_time DATETIME NOT NULL, PRIMARY KEY(tenant_id,case_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
