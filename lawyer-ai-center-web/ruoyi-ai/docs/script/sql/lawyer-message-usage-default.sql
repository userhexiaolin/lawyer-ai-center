-- Local compatibility adjustment after the preliminary default-0 migration.
ALTER TABLE chat_session ALTER COLUMN usage_started SET DEFAULT 1;
