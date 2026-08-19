BEGIN;

ALTER TABLE users
ADD COLUMN IF NOT EXISTS avatar_object_key VARCHAR(512);

ALTER TABLE users
DROP CONSTRAINT IF EXISTS user_avatar_object_key_check;

ALTER TABLE users
ADD CONSTRAINT user_avatar_object_key_check
CHECK (avatar_object_key IS NULL OR BTRIM(avatar_object_key) <> '');

COMMENT ON COLUMN users.avatar_object_key IS
'用户头像在对象存储中的对象 Key；不保存签名 URL、Bucket 密钥或完整访问地址';

COMMIT;

BEGIN;

CREATE TABLE IF NOT EXISTS practice_session (
    session_id VARCHAR(64) PRIMARY KEY,
    user_id UUID NOT NULL,
    scene_id VARCHAR(64),
    scene_type VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT practice_session_scene_type_check
        CHECK (scene_type IN (
			'FREE_CHAT', 'CUSTOM_SCENE', 'IELTS_SCENE'
        )),
    CONSTRAINT practice_session_status_check
        CHECK (status IN (
            'CREATED', 'CONNECTING', 'WAITING_CLIENT', 'ACTIVE',
            'PAUSED', 'INTERRUPTED', 'COMPLETED', 'FAILED'
        )),
    CONSTRAINT practice_session_time_check
        CHECK (ended_at IS NULL OR ended_at >= started_at)
);

CREATE INDEX IF NOT EXISTS idx_practice_session_user_started_at
ON practice_session (user_id, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_practice_session_user_completed_at
ON practice_session (user_id, ended_at DESC)
WHERE status = 'COMPLETED' AND ended_at IS NOT NULL;

COMMENT ON TABLE practice_session IS
'全场景练习会话事实；学习时长由 started_at 与 ended_at 计算，不保存聚合统计值';

COMMIT;
