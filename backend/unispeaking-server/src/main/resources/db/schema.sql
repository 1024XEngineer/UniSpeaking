CREATE TABLE IF NOT EXISTS "user" (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    nickname VARCHAR(32),
    role VARCHAR(16) NOT NULL DEFAULT 'USER',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    auth_version BIGINT NOT NULL DEFAULT 0,
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT user_role_check CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT user_status_check CHECK (status IN ('ACTIVE', 'DISABLED', 'LOCKED')),
    CONSTRAINT user_auth_version_check CHECK (auth_version >= 0)
);

ALTER TABLE "user"
ALTER COLUMN username TYPE VARCHAR(254);

CREATE TABLE IF NOT EXISTS user_preference (
    user_id UUID PRIMARY KEY,
    preferred_voice VARCHAR(64),
    preferred_ai_speech_speed VARCHAR(16) NOT NULL DEFAULT 'NATURAL',
    preferences JSONB NOT NULL DEFAULT '{}'::JSONB,
    memory_text TEXT,
    cefr_level VARCHAR(4),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON COLUMN user_preference.memory_text IS
'用户主动维护的长期档案摘要，仅包含兴趣与熟悉背景、昵称或称谓、年龄段、代词和敏感话题边界，不存储逐轮对话或会话历史摘要';

UPDATE user_preference
SET memory_text = NULL
WHERE memory_text LIKE '本次会话摘要（本地）：%';

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
