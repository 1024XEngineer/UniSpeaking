BEGIN;

CREATE TABLE IF NOT EXISTS scene (
    id VARCHAR(64) PRIMARY KEY,
    user_id UUID NOT NULL,
    title VARCHAR(128) NOT NULL,
    background TEXT NOT NULL,
    ai_role TEXT NOT NULL,
    user_role TEXT NOT NULL,
    learning_goal TEXT NOT NULL,
    custom_instruction TEXT,
    success_factor JSONB NOT NULL DEFAULT '{}'::JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT scene_id_check CHECK (id LIKE 'custom\_%' ESCAPE '\'),
    CONSTRAINT scene_title_check CHECK (BTRIM(title) <> ''),
    CONSTRAINT scene_success_factor_check
        CHECK (JSONB_TYPEOF(success_factor) = 'object')
);

CREATE INDEX IF NOT EXISTS idx_scene_user_id
ON scene (user_id);

CREATE INDEX IF NOT EXISTS idx_scene_active_user_updated_at
ON scene (user_id, updated_at DESC)
WHERE deleted_at IS NULL;

COMMENT ON TABLE scene IS
'用户创建的自定义场景定义；学习内容和练习结果由其他业务实体保存';

COMMENT ON COLUMN scene.user_id IS
'逻辑关联 user.id，不设置数据库外键';

COMMENT ON COLUMN scene.success_factor IS
'状态机用于判断场景是否成功完成的 JSON 条件对象';

COMMENT ON COLUMN scene.deleted_at IS
'软删除时间；为空表示场景有效';

CREATE OR REPLACE FUNCTION set_scene_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS scene_set_updated_at ON scene;

CREATE TRIGGER scene_set_updated_at
BEFORE UPDATE ON scene
FOR EACH ROW
EXECUTE FUNCTION set_scene_updated_at();

COMMIT;
