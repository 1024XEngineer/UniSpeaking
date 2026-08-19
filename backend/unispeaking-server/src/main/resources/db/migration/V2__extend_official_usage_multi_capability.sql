ALTER TABLE official_usage_records
    ALTER COLUMN task_uuid DROP NOT NULL;

ALTER TABLE official_usage_records
    ADD COLUMN IF NOT EXISTS characters BIGINT NOT NULL DEFAULT 0;
