-- Run once before deploying the new backend. Existing sessions were already charged.
ALTER TABLE chat_session ADD COLUMN usage_started TINYINT NOT NULL DEFAULT 1 COMMENT 'Server-owned first effective message charge marker';
-- Keep DEFAULT 1 for old backend instances, which still charge on creation.
-- The new service explicitly inserts 0. This also allows safe overlap during rollout.
