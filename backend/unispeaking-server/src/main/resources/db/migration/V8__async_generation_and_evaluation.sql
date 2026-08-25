CREATE TABLE IF NOT EXISTS custom_scene_generation_task (
    task_id VARCHAR(36) PRIMARY KEY,
    user_id UUID NOT NULL,
    scene_id VARCHAR(64) NOT NULL UNIQUE,
    scene_input VARCHAR(500) NOT NULL,
    user_preference TEXT,
    status VARCHAR(16) NOT NULL DEFAULT 'PROCESSING',
    result_json JSONB,
    failure_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT custom_scene_generation_task_status_check
        CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT custom_scene_generation_task_result_check
        CHECK ((status = 'COMPLETED' AND result_json IS NOT NULL)
            OR (status <> 'COMPLETED' AND result_json IS NULL)),
    CONSTRAINT custom_scene_generation_task_input_check
        CHECK (BTRIM(scene_input) <> '')
);

CREATE INDEX IF NOT EXISTS idx_custom_scene_generation_task_user_time
ON custom_scene_generation_task (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_custom_scene_generation_task_processing
ON custom_scene_generation_task (updated_at)
WHERE status = 'PROCESSING';

CREATE INDEX IF NOT EXISTS idx_practice_session_scene_started_at
ON practice_session (scene_id, started_at)
WHERE scene_id IS NOT NULL;
