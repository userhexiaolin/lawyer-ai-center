-- Apply after lawyer-legal-metadata.sql. Check each column so partial/repeated runs are safe.
-- Existing columns and data are preserved; deployment runner validates their definitions.
SET @stage4_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='knowledge_attach' AND column_name='case_no'), 'SELECT 1', 'ALTER TABLE knowledge_attach ADD COLUMN case_no VARCHAR(200) NULL COMMENT ''真实类案案号''');
PREPARE stage4_stmt FROM @stage4_ddl;
EXECUTE stage4_stmt;
DEALLOCATE PREPARE stage4_stmt;
SET @stage4_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='knowledge_attach' AND column_name='court'), 'SELECT 1', 'ALTER TABLE knowledge_attach ADD COLUMN court VARCHAR(300) NULL COMMENT ''审理法院''');
PREPARE stage4_stmt FROM @stage4_ddl;
EXECUTE stage4_stmt;
DEALLOCATE PREPARE stage4_stmt;
SET @stage4_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='knowledge_attach' AND column_name='source_url'), 'SELECT 1', 'ALTER TABLE knowledge_attach ADD COLUMN source_url VARCHAR(2000) NULL COMMENT ''可追溯的原文HTTP(S)地址''');
PREPARE stage4_stmt FROM @stage4_ddl;
EXECUTE stage4_stmt;
DEALLOCATE PREPARE stage4_stmt;
