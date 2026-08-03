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
            'FREE_CHAT',
            'CUSTOM_SCENE',
            'INTERVIEW_SCENE',
            'IELTS_SCENE'
        )),
    CONSTRAINT practice_session_status_check
        CHECK (status IN (
            'CREATED',
            'CONNECTING',
            'WAITING_CLIENT',
            'ACTIVE',
            'PAUSED',
            'INTERRUPTED',
            'COMPLETED',
            'FAILED'
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

COMMENT ON COLUMN practice_session.user_id IS
'逻辑关联 user.id，不设置数据库外键';

COMMENT ON COLUMN practice_session.scene_id IS
'场景业务 ID；自由对话和后续无需持久化场景定义的类型也允许记录';
