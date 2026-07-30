BEGIN;

CREATE TABLE IF NOT EXISTS "user" (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    nickname VARCHAR(32),
    avatar_object_key VARCHAR(512),
    role VARCHAR(16) NOT NULL DEFAULT 'USER',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    auth_version BIGINT NOT NULL DEFAULT 0,
    last_login_at TIMESTAMPTZ,
    deletion_requested_at TIMESTAMPTZ,
    deletion_scheduled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT user_role_check CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT user_status_check
        CHECK (status IN ('ACTIVE', 'DISABLED', 'LOCKED', 'PENDING_DELETION')),
    CONSTRAINT user_auth_version_check CHECK (auth_version >= 0),
    CONSTRAINT user_deletion_schedule_check CHECK (
        (status = 'PENDING_DELETION'
            AND deletion_requested_at IS NOT NULL
            AND deletion_scheduled_at IS NOT NULL
            AND deletion_scheduled_at > deletion_requested_at)
        OR
        (status <> 'PENDING_DELETION'
            AND deletion_requested_at IS NULL
            AND deletion_scheduled_at IS NULL)
    )
);

ALTER TABLE "user"
ALTER COLUMN username TYPE VARCHAR(254);

ALTER TABLE "user"
ADD COLUMN IF NOT EXISTS avatar_object_key VARCHAR(512),
ADD COLUMN IF NOT EXISTS deletion_requested_at TIMESTAMPTZ,
ADD COLUMN IF NOT EXISTS deletion_scheduled_at TIMESTAMPTZ;

ALTER TABLE "user"
DROP CONSTRAINT IF EXISTS user_status_check;

ALTER TABLE "user"
ADD CONSTRAINT user_status_check
CHECK (status IN ('ACTIVE', 'DISABLED', 'LOCKED', 'PENDING_DELETION'));

ALTER TABLE "user"
DROP CONSTRAINT IF EXISTS user_deletion_schedule_check;

ALTER TABLE "user"
ADD CONSTRAINT user_deletion_schedule_check CHECK (
    (status = 'PENDING_DELETION'
        AND deletion_requested_at IS NOT NULL
        AND deletion_scheduled_at IS NOT NULL
        AND deletion_scheduled_at > deletion_requested_at)
    OR
    (status <> 'PENDING_DELETION'
        AND deletion_requested_at IS NULL
        AND deletion_scheduled_at IS NULL)
);

CREATE TABLE IF NOT EXISTS user_preference (
    user_id UUID PRIMARY KEY,
    preferred_voice VARCHAR(64),
    preferred_ai_speech_speed VARCHAR(16) NOT NULL DEFAULT 'NATURAL',
    preferences JSONB NOT NULL DEFAULT '{}'::JSONB,
    memory_text TEXT,
    cefr_level VARCHAR(4),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT user_preference_cefr_level_check
        CHECK (cefr_level IS NULL OR cefr_level IN ('A', 'B', 'C', 'D')),
    CONSTRAINT user_preference_speech_speed_check
        CHECK (preferred_ai_speech_speed IN ('SLOWER', 'MODERATE', 'NATURAL', 'FASTER'))
);

COMMENT ON COLUMN user_preference.memory_text IS
'用户主动维护的长期档案摘要，仅包含兴趣与熟悉背景、昵称或称谓、年龄段、代词和敏感话题边界，不存储逐轮对话或会话历史摘要';

UPDATE user_preference
SET memory_text = NULL
WHERE memory_text LIKE '本次会话摘要（本地）：%';

INSERT INTO "user" (
    id,
    username,
    password_hash,
    nickname,
    role,
    status
)
VALUES (
    '11111111-1111-4111-8111-111111111111',
    'demo@example.com',
    '$2y$12$Oxho43Xny2DnCJ1BT2BtvOb5b6CeH6xNI/fa5wBT7jLLkvbHsPK9W',
    'Demo User',
    'USER',
    'ACTIVE'
)
ON CONFLICT (id) DO UPDATE
SET username = EXCLUDED.username,
    password_hash = EXCLUDED.password_hash,
    nickname = EXCLUDED.nickname,
    role = EXCLUDED.role,
    status = EXCLUDED.status,
    deletion_requested_at = NULL,
    deletion_scheduled_at = NULL;

DO $$
DECLARE
    current_user_id_type TEXT;
BEGIN
    SELECT data_type
    INTO current_user_id_type
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'user_preference'
      AND column_name = 'user_id';

    IF current_user_id_type <> 'uuid' THEN
        UPDATE user_preference
        SET user_id = '11111111-1111-4111-8111-111111111111'
        WHERE user_id = 'demo-user-001';

        ALTER TABLE user_preference
        ALTER COLUMN user_id TYPE UUID
        USING user_id::UUID;
    END IF;
END;
$$;

ALTER TABLE user_preference
DROP CONSTRAINT IF EXISTS user_preference_cefr_level_check;

UPDATE user_preference
SET cefr_level = CASE cefr_level
    WHEN 'A1' THEN 'A'
    WHEN 'A2' THEN 'B'
    WHEN 'B1' THEN 'C'
    WHEN 'B2' THEN 'D'
    WHEN 'C1' THEN 'D'
    WHEN 'C2' THEN 'D'
    ELSE cefr_level
END
WHERE cefr_level IS NOT NULL;

ALTER TABLE user_preference
ADD CONSTRAINT user_preference_cefr_level_check
CHECK (cefr_level IS NULL OR cefr_level IN ('A', 'B', 'C', 'D'));

ALTER TABLE user_preference
DROP CONSTRAINT IF EXISTS user_preference_speech_speed_check;

ALTER TABLE user_preference
ADD CONSTRAINT user_preference_speech_speed_check
CHECK (preferred_ai_speech_speed IN ('SLOWER', 'MODERATE', 'NATURAL', 'FASTER'));

CREATE TABLE IF NOT EXISTS achievement_definitions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(64) NOT NULL,
    description VARCHAR(255) NOT NULL,
    category VARCHAR(32) NOT NULL,
    metric_key VARCHAR(64) NOT NULL,
    target_value BIGINT NOT NULL,
    icon_key VARCHAR(64) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT achievement_target_value_check CHECK (target_value > 0),
    CONSTRAINT achievement_sort_order_check CHECK (sort_order >= 0),
    CONSTRAINT achievement_status_check CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX IF NOT EXISTS idx_achievement_definitions_active_order
ON achievement_definitions (status, sort_order, code);

CREATE TABLE IF NOT EXISTS user_achievement_progress (
    user_id UUID NOT NULL,
    achievement_id UUID NOT NULL,
    progress_value BIGINT NOT NULL DEFAULT 0,
    unlocked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, achievement_id),
    CONSTRAINT user_achievement_progress_user_fk
        FOREIGN KEY (user_id) REFERENCES "user"(id) ON DELETE CASCADE,
    CONSTRAINT user_achievement_progress_definition_fk
        FOREIGN KEY (achievement_id) REFERENCES achievement_definitions(id)
        ON DELETE RESTRICT,
    CONSTRAINT user_achievement_progress_value_check
        CHECK (progress_value >= 0)
);

CREATE INDEX IF NOT EXISTS idx_user_achievement_progress_unlocked
ON user_achievement_progress (user_id, unlocked_at);

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS user_set_updated_at ON "user";
CREATE TRIGGER user_set_updated_at
BEFORE UPDATE ON "user"
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS user_preference_set_updated_at ON user_preference;
CREATE TRIGGER user_preference_set_updated_at
BEFORE UPDATE ON user_preference
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

DROP FUNCTION IF EXISTS set_user_preference_updated_at();

INSERT INTO user_preference (
    user_id,
    preferred_voice,
    preferred_ai_speech_speed,
    preferences,
    memory_text,
    cefr_level
)
VALUES (
    '11111111-1111-4111-8111-111111111111',
    'Katerina',
    'NATURAL',
    '{"translation_enabled": true, "learning_goal": "daily_conversation"}'::JSONB,
    '兴趣与背景：喜欢科技、电影和旅行，从事软件产品相关工作，熟悉会议和演示场景。个人信息：昵称 Demo；未提供年龄段、代词和敏感话题边界。',
    'C'
)
ON CONFLICT (user_id) DO UPDATE
SET preferred_voice = EXCLUDED.preferred_voice,
    preferred_ai_speech_speed = EXCLUDED.preferred_ai_speech_speed,
    preferences = EXCLUDED.preferences,
    memory_text = EXCLUDED.memory_text,
    cefr_level = EXCLUDED.cefr_level;

COMMIT;
