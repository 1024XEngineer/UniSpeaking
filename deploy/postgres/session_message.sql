BEGIN;

CREATE TABLE IF NOT EXISTS session_message (
    scene_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    message_no INTEGER NOT NULL,
    owner SMALLINT NOT NULL,
    content TEXT NOT NULL,
    audio_url TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT session_message_pk
        PRIMARY KEY (session_id, message_no),
    CONSTRAINT session_message_scene_id_check
        CHECK (BTRIM(scene_id) <> ''),
    CONSTRAINT session_message_session_id_check
        CHECK (BTRIM(session_id) <> ''),
    CONSTRAINT session_message_no_check
        CHECK (message_no > 0),
    CONSTRAINT session_message_owner_check
        CHECK (owner IN (0, 1)),
    CONSTRAINT session_message_content_check
        CHECK (BTRIM(content) <> ''),
    CONSTRAINT session_message_audio_url_check
        CHECK (audio_url IS NULL OR BTRIM(audio_url) <> '')
);

CREATE INDEX IF NOT EXISTS idx_session_message_scene_id
ON session_message (scene_id);

CREATE INDEX IF NOT EXISTS idx_session_message_created_at
ON session_message (created_at DESC);

COMMENT ON TABLE session_message IS
'按消息保存自定义场景会话的最终完整转写，不保存流式 delta';

COMMENT ON COLUMN session_message.scene_id IS
'场景业务标识，不设置数据库外键';

COMMENT ON COLUMN session_message.session_id IS
'会话业务标识，不设置数据库外键';

COMMENT ON COLUMN session_message.message_no IS
'消息在当前会话中的顺序号，从 1 开始';

COMMENT ON COLUMN session_message.owner IS
'消息归属：0 表示 AI，1 表示用户';

COMMENT ON COLUMN session_message.content IS
'用户或 AI 的最终完整对话转写，不保存流式 delta';

COMMENT ON COLUMN session_message.audio_url IS
'可选的音频对象存储地址';

CREATE OR REPLACE FUNCTION set_session_message_update_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.update_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS session_message_set_update_at
ON session_message;

CREATE TRIGGER session_message_set_update_at
BEFORE UPDATE ON session_message
FOR EACH ROW
EXECUTE FUNCTION set_session_message_update_at();

COMMIT;
