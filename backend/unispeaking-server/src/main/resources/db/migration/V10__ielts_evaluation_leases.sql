ALTER TABLE ielts_part_evaluation
    ADD COLUMN IF NOT EXISTS lease_token VARCHAR(36),
    ADD COLUMN IF NOT EXISTS lease_expires_at TIMESTAMPTZ;

ALTER TABLE ielts_evaluation
    ADD COLUMN IF NOT EXISTS lease_token VARCHAR(36),
    ADD COLUMN IF NOT EXISTS lease_expires_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_ielts_part_evaluation_lease
ON ielts_part_evaluation (lease_expires_at)
WHERE evaluation_status = 'PENDING';

CREATE INDEX IF NOT EXISTS idx_ielts_evaluation_lease
ON ielts_evaluation (lease_expires_at)
WHERE evaluation_status = 'PENDING';
