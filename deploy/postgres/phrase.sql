BEGIN;

CREATE TABLE IF NOT EXISTS phrase (
    id VARCHAR(64) PRIMARY KEY,
    scene_id VARCHAR(64) NOT NULL,
    phrase VARCHAR(255) NOT NULL,
    phonetic VARCHAR(255),
    translation TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT phrase_id_check CHECK (id LIKE 'phrase\_%' ESCAPE '\'),
    CONSTRAINT phrase_scene_id_check CHECK (scene_id LIKE 'custom\_%' ESCAPE '\'),
    CONSTRAINT phrase_text_check CHECK (BTRIM(phrase) <> ''),
    CONSTRAINT phrase_translation_check CHECK (BTRIM(translation) <> '')
);

CREATE INDEX IF NOT EXISTS idx_phrase_scene_id
ON phrase (scene_id);

COMMENT ON TABLE phrase IS
'自定义场景的词组学习内容';

COMMENT ON COLUMN phrase.scene_id IS
'逻辑关联 scene.id，不设置数据库外键';

CREATE OR REPLACE FUNCTION set_phrase_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS phrase_set_updated_at ON phrase;

CREATE TRIGGER phrase_set_updated_at
BEFORE UPDATE ON phrase
FOR EACH ROW
EXECUTE FUNCTION set_phrase_updated_at();

COMMIT;
