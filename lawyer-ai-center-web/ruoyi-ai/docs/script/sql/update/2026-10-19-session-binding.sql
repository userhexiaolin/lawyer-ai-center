-- 74: execute before deploying the new backend. Re-runnable; existing chat tables are unchanged.
CREATE TABLE IF NOT EXISTS lawyer_session_binding (
 tenant_id varchar(20) NOT NULL,
 session_id bigint NOT NULL,
 user_id bigint NOT NULL,
 binding_json mediumtext NOT NULL,
 updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY (tenant_id, session_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- Rollback: first roll back application binaries; retain this table to preserve user selections.
-- Only drop this table after an explicit backup and approval to discard saved bindings.
