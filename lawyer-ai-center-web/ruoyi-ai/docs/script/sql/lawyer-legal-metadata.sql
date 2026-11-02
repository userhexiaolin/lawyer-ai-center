-- Apply once before deploying the matching backend. All columns optional for old data.
ALTER TABLE knowledge_attach
  ADD COLUMN law_name VARCHAR(300) NULL,
  ADD COLUMN article_no VARCHAR(100) NULL,
  ADD COLUMN region VARCHAR(100) NULL,
  ADD COLUMN validity_status VARCHAR(20) NULL,
  ADD COLUMN publish_date DATE NULL,
  ADD COLUMN expire_date DATE NULL,
  ADD COLUMN source_version VARCHAR(100) NULL;
