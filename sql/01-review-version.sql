-- Existing dedicated databases only; run once with the database administrator.
-- First verify information_schema.columns for the active database.
-- New databases already receive this column from src/main/resources/schema.sql.
-- No DROP/TRUNCATE, no update or deletion of existing reviews.
ALTER TABLE reviews ADD COLUMN version BIGINT NOT NULL DEFAULT 1;
