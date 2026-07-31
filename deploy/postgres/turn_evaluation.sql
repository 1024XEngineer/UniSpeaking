BEGIN;

CREATE TABLE IF NOT EXISTS turn_evaluation (
    session_id VARCHAR(64) NOT NULL,
    turn_no INTEGER NOT NULL,
    scene_id VARCHAR(64) NOT NULL,
    transcript TEXT NOT NULL,
    overall_score NUMERIC(5, 2) NOT NULL,
    rhythm_score NUMERIC(5, 2) NOT NULL,
    tone_score NUMERIC(5, 2),
    integrity_score NUMERIC(5, 2) NOT NULL,
    pronunciation_score NUMERIC(5, 2) NOT NULL,
    fluency_score NUMERIC(5, 2) NOT NULL,
    feedback_summary TEXT NOT NULL,
    suggested_expression TEXT NOT NULL,
    pronunciation_details JSONB NOT NULL DEFAULT '{}'::JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT turn_evaluation_pk PRIMARY KEY (session_id, turn_no),
    CONSTRAINT turn_evaluation_scene_id_check
        CHECK (BTRIM(scene_id) <> ''),
    CONSTRAINT turn_evaluation_session_id_check
        CHECK (BTRIM(session_id) <> ''),
    CONSTRAINT turn_evaluation_turn_no_check
        CHECK (turn_no > 0),
    CONSTRAINT turn_evaluation_transcript_check
        CHECK (BTRIM(transcript) <> ''),
    CONSTRAINT turn_evaluation_overall_score_check
        CHECK (overall_score BETWEEN 0 AND 100),
    CONSTRAINT turn_evaluation_rhythm_score_check
        CHECK (rhythm_score BETWEEN 0 AND 100),
    CONSTRAINT turn_evaluation_tone_score_check
        CHECK (tone_score IS NULL OR tone_score BETWEEN 0 AND 100),
    CONSTRAINT turn_evaluation_integrity_score_check
        CHECK (integrity_score BETWEEN 0 AND 100),
    CONSTRAINT turn_evaluation_pronunciation_score_check
        CHECK (pronunciation_score BETWEEN 0 AND 100),
    CONSTRAINT turn_evaluation_fluency_score_check
        CHECK (fluency_score BETWEEN 0 AND 100),
    CONSTRAINT turn_evaluation_feedback_summary_check
        CHECK (BTRIM(feedback_summary) <> ''),
    CONSTRAINT turn_evaluation_pronunciation_details_check
        CHECK (JSONB_TYPEOF(pronunciation_details) = 'object')
);

DO $$
BEGIN
    ALTER TABLE turn_evaluation
    DROP CONSTRAINT IF EXISTS turn_evaluation_session_turn_uk;
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'turn_evaluation'
          AND column_name = 'id'
    ) THEN
        ALTER TABLE turn_evaluation
        DROP CONSTRAINT IF EXISTS turn_evaluation_pkey;
        ALTER TABLE turn_evaluation
        DROP COLUMN id;
    END IF;
END;
$$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'turn_evaluation'::REGCLASS
          AND contype = 'p'
    ) THEN
        ALTER TABLE turn_evaluation
        ADD CONSTRAINT turn_evaluation_pk
        PRIMARY KEY (session_id, turn_no);
    END IF;
END;
$$;

CREATE INDEX IF NOT EXISTS idx_turn_evaluation_scene_id
ON turn_evaluation (scene_id);

CREATE INDEX IF NOT EXISTS idx_turn_evaluation_created_at
ON turn_evaluation (created_at DESC);

COMMENT ON TABLE turn_evaluation IS
'会话中每一轮用户发言的单句评分；session_id 与 turn_no 唯一确定一轮';

COMMENT ON COLUMN turn_evaluation.scene_id IS
'场景业务标识，不设置数据库外键';

COMMENT ON COLUMN turn_evaluation.session_id IS
'会话业务标识，不设置数据库外键';

COMMENT ON COLUMN turn_evaluation.turn_no IS
'用户发言在当前会话中的轮次，从 1 开始';

COMMENT ON COLUMN turn_evaluation.tone_score IS
'语调评分；供应商未返回该维度时允许为空';

COMMENT ON COLUMN turn_evaluation.pronunciation_details IS
'逐词和逐音素发音评分明细 JSON 对象';

CREATE OR REPLACE FUNCTION set_turn_evaluation_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS turn_evaluation_set_updated_at
ON turn_evaluation;

CREATE TRIGGER turn_evaluation_set_updated_at
BEFORE UPDATE ON turn_evaluation
FOR EACH ROW
EXECUTE FUNCTION set_turn_evaluation_updated_at();

COMMIT;
