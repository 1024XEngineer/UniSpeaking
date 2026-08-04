CREATE TABLE IF NOT EXISTS user_achievement_unlock (
    user_id UUID NOT NULL,
    achievement_id VARCHAR(64) NOT NULL,
    unlocked_at TIMESTAMPTZ NOT NULL,
    acknowledged_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT user_achievement_unlock_pk
        PRIMARY KEY (user_id, achievement_id),
    CONSTRAINT user_achievement_unlock_id_check
        CHECK (BTRIM(achievement_id) <> ''),
    CONSTRAINT user_achievement_unlock_acknowledged_at_check
        CHECK (acknowledged_at IS NULL OR acknowledged_at >= unlocked_at)
);

CREATE INDEX IF NOT EXISTS idx_user_achievement_unlock_pending
ON user_achievement_unlock (user_id, unlocked_at, achievement_id)
WHERE acknowledged_at IS NULL;

COMMENT ON TABLE user_achievement_unlock IS
'用户已解锁的成就节点及中央达成弹窗展示确认状态';

COMMENT ON COLUMN user_achievement_unlock.user_id IS
'逻辑关联 user.id，不设置数据库外键';

COMMENT ON COLUMN user_achievement_unlock.achievement_id IS
'服务端成就目录中的稳定节点 ID，例如 conversation-1';

COMMENT ON COLUMN user_achievement_unlock.unlocked_at IS
'服务端首次确认该节点达成的时间';

COMMENT ON COLUMN user_achievement_unlock.acknowledged_at IS
'中央达成弹窗已经展示并关闭的时间；为空表示等待展示';

CREATE TABLE IF NOT EXISTS user_achievement_state (
    user_id UUID PRIMARY KEY,
    initialized_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE user_achievement_state IS
'用户成就系统首次历史初始化状态';

COMMENT ON COLUMN user_achievement_state.user_id IS
'逻辑关联 user.id，不设置数据库外键';

COMMENT ON COLUMN user_achievement_state.initialized_at IS
'首次完成历史成就静默初始化的时间';
