BEGIN;

CREATE TABLE IF NOT EXISTS session_evaluation (
    session_id VARCHAR(64) PRIMARY KEY,
    scene_id VARCHAR(64),
    accuracy_score NUMERIC(5, 2) NOT NULL,
    fluency_score NUMERIC(5, 2) NOT NULL,
    grammar_score NUMERIC(5, 2) NOT NULL,
    vocabulary_score NUMERIC(5, 2) NOT NULL,
    naturalness_score NUMERIC(5, 2) NOT NULL,
    final_score NUMERIC(5, 2) NOT NULL,
    summary TEXT NOT NULL,
    strengths TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    improvements TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT session_evaluation_session_id_check
        CHECK (BTRIM(session_id) <> ''),
    CONSTRAINT session_evaluation_scene_id_check
        CHECK (scene_id IS NULL OR BTRIM(scene_id) <> ''),
    CONSTRAINT session_evaluation_accuracy_score_check
        CHECK (accuracy_score BETWEEN 0 AND 100),
    CONSTRAINT session_evaluation_fluency_score_check
        CHECK (fluency_score BETWEEN 0 AND 100),
    CONSTRAINT session_evaluation_grammar_score_check
        CHECK (grammar_score BETWEEN 0 AND 100),
    CONSTRAINT session_evaluation_vocabulary_score_check
        CHECK (vocabulary_score BETWEEN 0 AND 100),
    CONSTRAINT session_evaluation_naturalness_score_check
        CHECK (naturalness_score BETWEEN 0 AND 100),
    CONSTRAINT session_evaluation_final_score_check
        CHECK (final_score BETWEEN 0 AND 100),
    CONSTRAINT session_evaluation_summary_check
        CHECK (BTRIM(summary) <> '')
);

ALTER TABLE session_evaluation
ADD COLUMN IF NOT EXISTS scene_id VARCHAR(64);

DO $$
BEGIN
    IF TO_REGCLASS('public.session_message') IS NOT NULL THEN
        EXECUTE '
            UPDATE session_evaluation AS evaluation
            SET scene_id = message.scene_id
            FROM (
                SELECT DISTINCT ON (session_id) session_id, scene_id
                FROM session_message
                ORDER BY session_id, message_no
            ) AS message
            WHERE evaluation.session_id = message.session_id
              AND evaluation.scene_id IS NULL
        ';
    END IF;
END;
$$;

CREATE INDEX IF NOT EXISTS idx_session_evaluation_scene_id
ON session_evaluation (scene_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_session_evaluation_created_at
ON session_evaluation (created_at DESC);

COMMENT ON TABLE session_evaluation IS
'一场会话结束后的综合练习评分；每个 session_id 只保存一份结果';

COMMENT ON COLUMN session_evaluation.session_id IS
'会话业务标识，与其他会话表保持 VARCHAR(64)，不设置数据库外键';

COMMENT ON COLUMN session_evaluation.scene_id IS
'场景业务标识，用于保留同一场景的历次五维评分，不设置数据库外键';

COMMENT ON COLUMN session_evaluation.strengths IS
'本场会话表现较好的方面';

COMMENT ON COLUMN session_evaluation.improvements IS
'本场会话需要改进的方面';

CREATE OR REPLACE FUNCTION set_session_evaluation_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS session_evaluation_set_updated_at
ON session_evaluation;

CREATE TRIGGER session_evaluation_set_updated_at
BEFORE UPDATE ON session_evaluation
FOR EACH ROW
EXECUTE FUNCTION set_session_evaluation_updated_at();

COMMIT;
