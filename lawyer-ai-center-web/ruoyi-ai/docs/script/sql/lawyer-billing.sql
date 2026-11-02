-- Phase 3 billing. Apply once after the previous lawyer migrations; payment remains disabled.
CREATE TABLE IF NOT EXISTS lawyer_billing_plan (
 code VARCHAR(40) PRIMARY KEY, name VARCHAR(100) NOT NULL, price DECIMAL(10,2) NOT NULL,
 chat_credits INT NOT NULL, review_credits INT NOT NULL, valid_days INT NOT NULL,
 member_days INT NOT NULL DEFAULT 0, enabled TINYINT NOT NULL DEFAULT 0, sort_order INT NOT NULL DEFAULT 0,
 CHECK(price>0 AND chat_credits>=0 AND review_credits>=0 AND valid_days>0 AND member_days>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS lawyer_billing_account (
 tenant_id VARCHAR(20) NOT NULL,user_id BIGINT NOT NULL,member_until DATETIME NULL,
 PRIMARY KEY(tenant_id,user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS lawyer_billing_order (
 order_no VARCHAR(32) PRIMARY KEY,tenant_id VARCHAR(20) NOT NULL,user_id BIGINT NOT NULL,
 plan_code VARCHAR(40) NOT NULL,plan_name VARCHAR(100) NOT NULL,amount DECIMAL(10,2) NOT NULL,
 chat_credits INT NOT NULL,review_credits INT NOT NULL,valid_days INT NOT NULL,member_days INT NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'PENDING',channel VARCHAR(20) NOT NULL DEFAULT 'ALIPAY',
 app_id VARCHAR(40) NOT NULL,trade_no VARCHAR(80) NULL,pay_url VARCHAR(1000) NULL,
 idempotency_key VARCHAR(64) NOT NULL,created_at DATETIME NOT NULL,expires_at DATETIME NOT NULL,paid_at DATETIME NULL,
 UNIQUE KEY uk_order_idempotency(tenant_id,user_id,idempotency_key),
 UNIQUE KEY uk_provider_trade(channel,trade_no),KEY idx_order_owner(tenant_id,user_id,created_at),KEY idx_order_metrics(status,paid_at),
 CHECK(status IN ('PENDING','PAID','CLOSED','REFUNDED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS lawyer_billing_grant (
 id VARCHAR(32) PRIMARY KEY,tenant_id VARCHAR(20) NOT NULL,user_id BIGINT NOT NULL,order_no VARCHAR(32) NULL,
 remaining_chat INT NOT NULL,remaining_review INT NOT NULL,member_days INT NOT NULL DEFAULT 0,
 granted_at DATETIME NOT NULL,expires_at DATETIME NOT NULL,revoked TINYINT NOT NULL DEFAULT 0,
 UNIQUE KEY uk_grant_order(order_no),KEY idx_grant_owner(tenant_id,user_id,revoked,expires_at),
 CHECK(remaining_chat>=0 AND remaining_review>=0 AND member_days>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS lawyer_billing_ledger (
 id VARCHAR(32) PRIMARY KEY,tenant_id VARCHAR(20) NOT NULL,user_id BIGINT NOT NULL,grant_id VARCHAR(32) NULL,
 order_no VARCHAR(32) NULL,kind VARCHAR(24) NOT NULL,chat_delta INT NOT NULL,review_delta INT NOT NULL,
 actor_user_id BIGINT NULL,note VARCHAR(200) NOT NULL,created_at DATETIME NOT NULL,
 KEY idx_ledger_owner(tenant_id,user_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT IGNORE INTO lawyer_billing_plan VALUES
 ('credits-starter','基础次数包',29.90,100,10,90,0,0,10),
 ('member-monthly','月度会员',99.00,300,30,30,30,0,20);
-- Packages are intentionally offline until the owner approves prices and enables the merchant.
ALTER TABLE lawyer_daily_usage ADD COLUMN used_free_sessions INT NOT NULL DEFAULT 0;
UPDATE lawyer_daily_usage SET used_free_sessions=used_sessions;
ALTER TABLE chat_session ADD COLUMN usage_kind VARCHAR(10) NOT NULL DEFAULT 'CHAT';
UPDATE chat_session SET usage_kind='REVIEW' WHERE session_title LIKE '合同审查 - %' OR session_title LIKE '合同快审 - %';
