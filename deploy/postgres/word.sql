BEGIN;

CREATE TABLE IF NOT EXISTS "word" (
    scene_id VARCHAR(64) NOT NULL,
    word_id VARCHAR(64) NOT NULL,
    word VARCHAR(128) NOT NULL,
    phonetic VARCHAR(128),
    translation TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT word_pk PRIMARY KEY (scene_id, word_id),
    CONSTRAINT word_id_check CHECK (word_id LIKE 'word\_%' ESCAPE '\'),
    CONSTRAINT word_scene_id_check CHECK (scene_id LIKE 'custom\_%' ESCAPE '\'),
    CONSTRAINT word_text_check CHECK (BTRIM(word) <> ''),
    CONSTRAINT word_translation_check CHECK (BTRIM(translation) <> '')
);

ALTER TABLE "word"
DROP CONSTRAINT IF EXISTS word_pkey;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = '"word"'::REGCLASS
          AND contype = 'p'
    ) THEN
        ALTER TABLE "word"
        ADD CONSTRAINT word_pk PRIMARY KEY (scene_id, word_id);
    END IF;
END;
$$;

COMMENT ON TABLE "word" IS
'自定义场景的单词学习内容';

COMMENT ON COLUMN "word".scene_id IS
'逻辑关联 scene.id，不设置数据库外键';

COMMENT ON COLUMN "word".word_id IS
'单词业务标识，与 scene_id 共同构成主键';

CREATE OR REPLACE FUNCTION set_word_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS word_set_updated_at ON "word";

CREATE TRIGGER word_set_updated_at
BEFORE UPDATE ON "word"
FOR EACH ROW
EXECUTE FUNCTION set_word_updated_at();

COMMIT;
