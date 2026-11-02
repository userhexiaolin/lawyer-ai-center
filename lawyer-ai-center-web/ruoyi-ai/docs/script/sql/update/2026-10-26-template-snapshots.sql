-- Add before deploying the snapshot writer. Backfill existing rows with the migration utility.
-- Additive migration: original binary files and legacy blocks_json remain unchanged.
ALTER TABLE legal_doc_template_file
 ADD COLUMN snapshot_json MEDIUMTEXT NULL COMMENT 'Versioned complete field and parse-state snapshot';
