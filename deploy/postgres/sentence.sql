BEGIN;

CREATE TABLE IF NOT EXISTS sentence (
    id VARCHAR(64) PRIMARY KEY,
    sentence_id VARCHAR(64) NOT NULL,
    scene_id VARCHAR(64) NOT NULL,
    sentence TEXT NOT NULL,
    translation TEXT NOT NULL,
    overall_score NUMERIC(5, 2),
    score_detail JSONB NOT NULL DEFAULT '{}'::JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT sentence_record_id_check
        CHECK (id LIKE 'sentence\_reading\_%' ESCAPE '\'),
    CONSTRAINT sentence_content_id_check
        CHECK (sentence_id LIKE 'sentence\_%' ESCAPE '\'),
    CONSTRAINT sentence_scene_id_check
        CHECK (scene_id LIKE 'custom\_%' ESCAPE '\'),
    CONSTRAINT sentence_text_check CHECK (BTRIM(sentence) <> ''),
    CONSTRAINT sentence_translation_check CHECK (BTRIM(translation) <> ''),
    CONSTRAINT sentence_overall_score_check
        CHECK (overall_score IS NULL OR overall_score BETWEEN 0 AND 100),
    CONSTRAINT sentence_score_detail_check
        CHECK (JSONB_TYPEOF(score_detail) = 'object')
);

CREATE INDEX IF NOT EXISTS idx_sentence_scene_id
ON sentence (scene_id);

CREATE INDEX IF NOT EXISTS idx_sentence_content_id
ON sentence (sentence_id);

CREATE INDEX IF NOT EXISTS idx_sentence_scene_content_created_at
ON sentence (scene_id, sentence_id, created_at DESC);

COMMENT ON TABLE sentence IS
'自定义场景的参考句子及用户重复朗读记录';

COMMENT ON COLUMN sentence.id IS
'每次朗读记录的唯一标识；同一句子重复朗读时生成不同 id';

COMMENT ON COLUMN sentence.sentence_id IS
'参考句子的业务标识；同一句子的多次朗读记录共享该值';

COMMENT ON COLUMN sentence.scene_id IS
'逻辑关联 scene.id，不设置数据库外键';

COMMENT ON COLUMN sentence.score_detail IS
'JSON 格式的评分细则；未评分时为空对象';

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

COMMIT;
