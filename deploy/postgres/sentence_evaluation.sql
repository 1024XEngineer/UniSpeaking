BEGIN;

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

CREATE INDEX IF NOT EXISTS idx_sentence_evaluation_scene_sentence
ON sentence_evaluation (scene_id, sentence_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_sentence_evaluation_created_at
ON sentence_evaluation (created_at DESC);

COMMENT ON TABLE sentence_evaluation IS
'用户每次朗读场景句子的评分记录';

COMMENT ON COLUMN sentence_evaluation.scene_id IS
'场景业务标识，不设置数据库外键';

COMMENT ON COLUMN sentence_evaluation.sentence_id IS
'句子业务标识，与 scene_id 一起定位 sentence 资产，不设置数据库外键';

COMMENT ON COLUMN sentence_evaluation.score_detail IS
'JSON 格式的逐词及逐音素评分细则';

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
