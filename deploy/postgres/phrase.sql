BEGIN;

CREATE TABLE IF NOT EXISTS phrase (
    scene_id VARCHAR(64) NOT NULL,
    phrase_id VARCHAR(64) NOT NULL,
    phrase VARCHAR(255) NOT NULL,
    phonetic VARCHAR(255),
    translation TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT phrase_pk PRIMARY KEY (scene_id, phrase_id),
    CONSTRAINT phrase_id_check
        CHECK (phrase_id LIKE 'phrase\_%' ESCAPE '\'),
    CONSTRAINT phrase_scene_id_check CHECK (scene_id LIKE 'custom\_%' ESCAPE '\'),
    CONSTRAINT phrase_text_check CHECK (BTRIM(phrase) <> ''),
    CONSTRAINT phrase_translation_check CHECK (BTRIM(translation) <> '')
);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'phrase'
          AND column_name = 'id'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'phrase'
          AND column_name = 'phrase_id'
    ) THEN
        ALTER TABLE phrase RENAME COLUMN id TO phrase_id;
    END IF;
END;
$$;

ALTER TABLE phrase
DROP CONSTRAINT IF EXISTS phrase_pkey;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'phrase'::REGCLASS
          AND contype = 'p'
    ) THEN
        ALTER TABLE phrase
        ADD CONSTRAINT phrase_pk PRIMARY KEY (scene_id, phrase_id);
    END IF;
END;
$$;

COMMENT ON TABLE phrase IS
'自定义场景的词组学习内容';

COMMENT ON COLUMN phrase.scene_id IS
'逻辑关联 scene.id，不设置数据库外键';

COMMENT ON COLUMN phrase.phrase_id IS
'词组业务标识，与 scene_id 共同构成主键';

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
