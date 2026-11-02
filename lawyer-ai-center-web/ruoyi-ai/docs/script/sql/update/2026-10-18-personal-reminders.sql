-- Reuse sys_notice. NULL recipient preserves existing announcement rows and semantics.
-- Run after case-tasks; each step checks the current schema before additive DDL.
SET @notice_ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='sys_notice' AND column_name='recipient_user_id'),'SELECT 1','ALTER TABLE sys_notice ADD COLUMN recipient_user_id BIGINT NULL COMMENT ''个人通知收件人，NULL为公告''');
PREPARE notice_stmt FROM @notice_ddl;
EXECUTE notice_stmt;
DEALLOCATE PREPARE notice_stmt;
SET @notice_ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='sys_notice' AND column_name='reminder_key'),'SELECT 1','ALTER TABLE sys_notice ADD COLUMN reminder_key VARCHAR(191) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''业务去重键''');
PREPARE notice_stmt FROM @notice_ddl;
EXECUTE notice_stmt;
DEALLOCATE PREPARE notice_stmt;
SET @notice_ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='sys_notice' AND column_name='related_case_id'),'SELECT 1','ALTER TABLE sys_notice ADD COLUMN related_case_id BIGINT NULL');
PREPARE notice_stmt FROM @notice_ddl;
EXECUTE notice_stmt;
DEALLOCATE PREPARE notice_stmt;
SET @notice_ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='sys_notice' AND column_name='read_time'),'SELECT 1','ALTER TABLE sys_notice ADD COLUMN read_time DATETIME NULL');
PREPARE notice_stmt FROM @notice_ddl;
EXECUTE notice_stmt;
DEALLOCATE PREPARE notice_stmt;
SET @notice_ddl=IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='sys_notice' AND index_name='uk_notice_recipient_key'),'SELECT 1','ALTER TABLE sys_notice ADD UNIQUE KEY uk_notice_recipient_key(tenant_id,recipient_user_id,reminder_key)');
PREPARE notice_stmt FROM @notice_ddl;
EXECUTE notice_stmt;
DEALLOCATE PREPARE notice_stmt;
SET @notice_ddl=IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='sys_notice' AND index_name='idx_notice_inbox'),'SELECT 1','ALTER TABLE sys_notice ADD KEY idx_notice_inbox(tenant_id,recipient_user_id,status,notice_id)');
PREPARE notice_stmt FROM @notice_ddl;
EXECUTE notice_stmt;
DEALLOCATE PREPARE notice_stmt;
