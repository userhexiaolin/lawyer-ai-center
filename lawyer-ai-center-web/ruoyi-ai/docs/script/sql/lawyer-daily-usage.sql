-- One dimension for MVP: successfully created sessions (consultation and review share quota).
CREATE TABLE IF NOT EXISTS lawyer_daily_usage (
  tenant_id VARCHAR(20) NOT NULL,
  user_id BIGINT NOT NULL,
  usage_date DATE NOT NULL,
  used_sessions INT NOT NULL DEFAULT 0,
  session_limit INT NOT NULL DEFAULT 50,
  PRIMARY KEY (tenant_id, user_id, usage_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
