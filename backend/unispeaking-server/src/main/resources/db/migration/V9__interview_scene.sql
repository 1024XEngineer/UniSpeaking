-- Interview 场景第一刀：Interview 场景资产 + 最终报告。
-- 生产 Flyway baseline=8（ADR-8 双轨，见 deploy/env/.env.prod.example），本地 V1/V2 冻结，
-- 全部 Interview schema 进入 V9。只建 2 张新表（O1/D3：不建 interview_turn，不写 turn_evaluation）。

-- 1) practice_session.scene_type 重加 INTERVIEW_SCENE
-- V2 曾删除该值（V2__remove_retired_interview_schema.sql:29-38），V9 重建，使 Interview 会话
-- 与 Custom/IELTS 统一落 practice_session 聚合面。
ALTER TABLE practice_session
DROP CONSTRAINT IF EXISTS practice_session_scene_type_check;

ALTER TABLE practice_session
ADD CONSTRAINT practice_session_scene_type_check
CHECK (scene_type IN (
    'FREE_CHAT',
    'CUSTOM_SCENE',
    'IELTS_SCENE',
    'INTERVIEW_SCENE'
));

-- 2) interview_scene（面试场景资产）
-- 无外键（同 practice_session，逻辑关联）；软删 deleted_at 支持后端删除。
CREATE TABLE interview_scene (
    scene_id VARCHAR(64) PRIMARY KEY,
    user_id UUID NOT NULL,
    confirmed_material JSONB NOT NULL,
    final_text TEXT NOT NULL,
    interview_context JSONB NOT NULL,
    difficulty VARCHAR(16) NOT NULL,
    scene_prompt TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT interview_scene_id_check CHECK (scene_id ~ '^interview_[A-Za-z0-9]+$'),
    CONSTRAINT interview_scene_difficulty_check CHECK (difficulty IN ('EASY','STANDARD','HARD')),
    CONSTRAINT interview_scene_material_check CHECK (JSONB_TYPEOF(confirmed_material) = 'object'),
    CONSTRAINT interview_scene_context_check CHECK (JSONB_TYPEOF(interview_context) = 'object'),
    CONSTRAINT interview_scene_final_text_check CHECK (BTRIM(final_text) <> ''),
    CONSTRAINT interview_scene_prompt_check CHECK (BTRIM(scene_prompt) <> '')
);

CREATE INDEX idx_interview_scene_user_updated
    ON interview_scene (user_id, updated_at DESC) WHERE deleted_at IS NULL;

CREATE OR REPLACE FUNCTION set_interview_scene_updated_at()
RETURNS TRIGGER
AS 'BEGIN NEW.updated_at = CURRENT_TIMESTAMP; RETURN NEW; END;'
LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS interview_scene_set_updated_at ON interview_scene;

CREATE TRIGGER interview_scene_set_updated_at
BEFORE UPDATE ON interview_scene
FOR EACH ROW
EXECUTE FUNCTION set_interview_scene_updated_at();

-- 3) interview_report（最终报告 + 生命周期态）
-- 行即任务（N6：不建 evaluation_task）。五维 × (score + evaluation + advice) 全部落库；
-- overall_score 由整场 LLM 综合判断并落库。updated_at 承担 completedAt 投影与
-- PROCESSING 清扫新鲜度，由 BEFORE UPDATE 触发器自动维护。
CREATE TABLE interview_report (
    session_id VARCHAR(64) PRIMARY KEY,
    scene_id VARCHAR(64) NOT NULL,
    user_id UUID NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PROCESSING',  -- PROCESSING/COMPLETED/FAILED
    summary TEXT,
    overall_score NUMERIC(5,2),
    fluency_score NUMERIC(5,2),
    fluency_evaluation TEXT,
    fluency_advice TEXT,
    pronunciation_intelligibility_score NUMERIC(5,2),
    pronunciation_intelligibility_evaluation TEXT,
    pronunciation_intelligibility_advice TEXT,
    logic_coherence_score NUMERIC(5,2),
    logic_coherence_evaluation TEXT,
    logic_coherence_advice TEXT,
    grammar_control_score NUMERIC(5,2),
    grammar_control_evaluation TEXT,
    grammar_control_advice TEXT,
    vocabulary_expression_score NUMERIC(5,2),
    vocabulary_expression_evaluation TEXT,
    vocabulary_expression_advice TEXT,
    retry_count SMALLINT NOT NULL DEFAULT 0,
    failure_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT interview_report_status_check CHECK (status IN ('PROCESSING','COMPLETED','FAILED')),
    -- A2 审计补：PostgreSQL 的 CHECK 在表达式为 NULL 时通过，故"必填"须用 IS NOT NULL 显式表达；
    -- overall 是 LLM 独立判断故 COMPLETED 必填；每维 score 允许 NULL（覆盖率降级：无有效语音→发音维度 NULL+标注）
    CONSTRAINT interview_report_score_check CHECK (
        (status = 'COMPLETED'
            AND overall_score IS NOT NULL AND overall_score BETWEEN 0 AND 100
            AND (fluency_score IS NULL OR fluency_score BETWEEN 0 AND 100)
            AND (pronunciation_intelligibility_score IS NULL OR pronunciation_intelligibility_score BETWEEN 0 AND 100)
            AND (logic_coherence_score IS NULL OR logic_coherence_score BETWEEN 0 AND 100)
            AND (grammar_control_score IS NULL OR grammar_control_score BETWEEN 0 AND 100)
            AND (vocabulary_expression_score IS NULL OR vocabulary_expression_score BETWEEN 0 AND 100))
        OR (status <> 'COMPLETED'
            AND overall_score IS NULL AND fluency_score IS NULL
            AND pronunciation_intelligibility_score IS NULL
            AND logic_coherence_score IS NULL AND grammar_control_score IS NULL
            AND vocabulary_expression_score IS NULL)),
    -- A2 审计补：D2 曾定义、V6 丢失，COMPLETED 时 summary 必填
    CONSTRAINT interview_report_summary_check CHECK (
        (status = 'COMPLETED' AND BTRIM(summary) <> '')
        OR (status <> 'COMPLETED' AND summary IS NULL)),
    CONSTRAINT interview_report_retry_check CHECK (retry_count >= 0),
    -- P3：FAILED → failure_reason 非空，否则 NULL
    CONSTRAINT interview_report_failure_check CHECK (
        (status = 'FAILED' AND BTRIM(failure_reason) <> '')
        OR (status <> 'FAILED' AND failure_reason IS NULL))
);

CREATE INDEX idx_interview_report_status_updated
    ON interview_report (updated_at) WHERE status = 'PROCESSING';
CREATE INDEX idx_interview_report_scene_created
    ON interview_report (scene_id, created_at DESC);

CREATE OR REPLACE FUNCTION set_interview_report_updated_at()
RETURNS TRIGGER
AS 'BEGIN NEW.updated_at = CURRENT_TIMESTAMP; RETURN NEW; END;'
LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS interview_report_set_updated_at ON interview_report;

CREATE TRIGGER interview_report_set_updated_at
BEFORE UPDATE ON interview_report
FOR EACH ROW
EXECUTE FUNCTION set_interview_report_updated_at();
