BEGIN;

CREATE TABLE IF NOT EXISTS sentence (
    scene_id VARCHAR(64) NOT NULL,
    sentence_id VARCHAR(64) NOT NULL,
    sentence TEXT NOT NULL,
    translation TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT sentence_pk PRIMARY KEY (scene_id, sentence_id),
    CONSTRAINT sentence_content_id_check
        CHECK (sentence_id LIKE 'sentence\_%' ESCAPE '\'),
    CONSTRAINT sentence_scene_id_check
        CHECK (scene_id LIKE 'custom\_%' ESCAPE '\'),
    CONSTRAINT sentence_text_check CHECK (BTRIM(sentence) <> ''),
    CONSTRAINT sentence_translation_check CHECK (BTRIM(translation) <> '')
);

CREATE TABLE IF NOT EXISTS sentence_evaluation (
    id VARCHAR(64) PRIMARY KEY,
    scene_id VARCHAR(64) NOT NULL,
    sentence_id VARCHAR(64) NOT NULL,
    overall_score NUMERIC(5, 2) NOT NULL,
    score_detail JSONB NOT NULL DEFAULT '{}'::JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT sentence_evaluation_id_check
        CHECK (id LIKE 'sentence\_reading\_%' ESCAPE '\'),
    CONSTRAINT sentence_evaluation_scene_id_check
        CHECK (scene_id LIKE 'custom\_%' ESCAPE '\'),
    CONSTRAINT sentence_evaluation_sentence_id_check
        CHECK (sentence_id LIKE 'sentence\_%' ESCAPE '\'),
    CONSTRAINT sentence_evaluation_overall_score_check
        CHECK (overall_score BETWEEN 0 AND 100),
    CONSTRAINT sentence_evaluation_score_detail_check
        CHECK (JSONB_TYPEOF(score_detail) = 'object')
);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'sentence'
          AND column_name = 'id'
    ) THEN
        EXECUTE '
            INSERT INTO sentence_evaluation (
                id,
                scene_id,
                sentence_id,
                overall_score,
                score_detail,
                created_at,
                updated_at
            )
            SELECT
                id,
                scene_id,
                sentence_id,
                overall_score,
                score_detail,
                created_at,
                updated_at
            FROM sentence
            WHERE overall_score IS NOT NULL
            ON CONFLICT (id) DO NOTHING
        ';
        EXECUTE '
            DELETE FROM sentence AS duplicate
            USING sentence AS retained
            WHERE duplicate.scene_id = retained.scene_id
              AND duplicate.sentence_id = retained.sentence_id
              AND (duplicate.created_at, duplicate.id)
                  > (retained.created_at, retained.id)
        ';
        ALTER TABLE sentence
        DROP CONSTRAINT IF EXISTS sentence_pkey;
        ALTER TABLE sentence
        DROP CONSTRAINT IF EXISTS sentence_record_id_check;
        ALTER TABLE sentence
        DROP CONSTRAINT IF EXISTS sentence_overall_score_check;
        ALTER TABLE sentence
        DROP CONSTRAINT IF EXISTS sentence_score_detail_check;
        ALTER TABLE sentence
        DROP COLUMN id;
        ALTER TABLE sentence
        DROP COLUMN overall_score;
        ALTER TABLE sentence
        DROP COLUMN score_detail;
    END IF;
END;
$$;

ALTER TABLE sentence
DROP CONSTRAINT IF EXISTS sentence_pk;

ALTER TABLE sentence
ADD CONSTRAINT sentence_pk PRIMARY KEY (scene_id, sentence_id);

DROP INDEX IF EXISTS idx_sentence_scene_id;

CREATE INDEX IF NOT EXISTS idx_sentence_content_id
ON sentence (sentence_id);

DROP INDEX IF EXISTS idx_sentence_scene_content_created_at;

CREATE INDEX IF NOT EXISTS idx_sentence_evaluation_scene_sentence
ON sentence_evaluation (scene_id, sentence_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_sentence_evaluation_created_at
ON sentence_evaluation (created_at DESC);

COMMENT ON TABLE sentence IS
'自定义场景的参考句子资产，不保存朗读评分';

COMMENT ON COLUMN sentence.scene_id IS
'场景业务标识，与 sentence_id 共同构成主键';

COMMENT ON COLUMN sentence.sentence_id IS
'参考句子的业务标识，与 scene_id 共同构成主键';

COMMENT ON TABLE sentence_evaluation IS
'用户每次朗读场景句子的评分记录';

COMMENT ON COLUMN sentence_evaluation.scene_id IS
'场景业务标识，不设置数据库外键';

COMMENT ON COLUMN sentence_evaluation.sentence_id IS
'句子业务标识，与 scene_id 一起定位 sentence 资产，不设置数据库外键';

COMMENT ON COLUMN sentence_evaluation.score_detail IS
'JSON 格式的逐词及逐音素评分细则';

CREATE OR REPLACE FUNCTION set_sentence_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS sentence_set_updated_at ON sentence;

CREATE TRIGGER sentence_set_updated_at
BEFORE UPDATE ON sentence
FOR EACH ROW
EXECUTE FUNCTION set_sentence_updated_at();

CREATE OR REPLACE FUNCTION set_sentence_evaluation_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS sentence_evaluation_set_updated_at
ON sentence_evaluation;

CREATE TRIGGER sentence_evaluation_set_updated_at
BEFORE UPDATE ON sentence_evaluation
FOR EACH ROW
EXECUTE FUNCTION set_sentence_evaluation_updated_at();

COMMIT;
