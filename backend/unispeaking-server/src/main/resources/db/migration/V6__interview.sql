CREATE TABLE interview (
    id VARCHAR(64) PRIMARY KEY,
    user_id UUID NOT NULL,
    session_id VARCHAR(64) NOT NULL UNIQUE,
    job_title VARCHAR(255) NOT NULL,
    difficulty VARCHAR(16) NOT NULL,
    role_summary JSONB NOT NULL,
    recording_object_key VARCHAR(512),
    recording_duration_seconds INTEGER,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT interview_difficulty_check
        CHECK (difficulty IN ('BASIC', 'STANDARD', 'CHALLENGE')),
    CONSTRAINT interview_recording_duration_check
        CHECK (recording_duration_seconds IS NULL
            OR recording_duration_seconds >= 0)
);

CREATE INDEX idx_interview_user_completed_at
ON interview (user_id, completed_at DESC)
WHERE completed_at IS NOT NULL;

CREATE TABLE interview_question (
    interview_id VARCHAR(64) NOT NULL,
    question_no INTEGER NOT NULL,
    question_type VARCHAR(16) NOT NULL,
    question_text TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT interview_question_pk
        PRIMARY KEY (interview_id, question_no),
    CONSTRAINT interview_question_no_check
        CHECK (question_no >= 1),
    CONSTRAINT interview_question_type_check
        CHECK (question_type IN ('MAIN', 'FOLLOW_UP'))
);

CREATE TABLE interview_report (
    interview_id VARCHAR(64) PRIMARY KEY,
    report_type VARCHAR(16) NOT NULL,
    overall_score NUMERIC(4, 1) NOT NULL,
    overall_summary TEXT NOT NULL,
    fluency_score NUMERIC(4, 1) NOT NULL,
    fluency_evaluation TEXT NOT NULL,
    fluency_action_suggestion TEXT NOT NULL,
    logic_coherence_score NUMERIC(4, 1) NOT NULL,
    logic_coherence_evaluation TEXT NOT NULL,
    logic_coherence_action_suggestion TEXT NOT NULL,
    grammar_control_score NUMERIC(4, 1) NOT NULL,
    grammar_control_evaluation TEXT NOT NULL,
    grammar_control_action_suggestion TEXT NOT NULL,
    pronunciation_intelligibility_score NUMERIC(4, 1) NOT NULL,
    pronunciation_intelligibility_evaluation TEXT NOT NULL,
    pronunciation_intelligibility_action_suggestion TEXT NOT NULL,
    vocabulary_expression_score NUMERIC(4, 1) NOT NULL,
    vocabulary_expression_evaluation TEXT NOT NULL,
    vocabulary_expression_action_suggestion TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT interview_report_type_check
        CHECK (report_type IN ('FULL', 'PARTIAL')),
    CONSTRAINT interview_report_overall_score_check
        CHECK (overall_score BETWEEN 0 AND 100),
    CONSTRAINT interview_report_fluency_score_check
        CHECK (fluency_score BETWEEN 0 AND 100),
    CONSTRAINT interview_report_logic_coherence_score_check
        CHECK (logic_coherence_score BETWEEN 0 AND 100),
    CONSTRAINT interview_report_grammar_control_score_check
        CHECK (grammar_control_score BETWEEN 0 AND 100),
    CONSTRAINT interview_report_pronunciation_intelligibility_score_check
        CHECK (pronunciation_intelligibility_score BETWEEN 0 AND 100),
    CONSTRAINT interview_report_vocabulary_expression_score_check
        CHECK (vocabulary_expression_score BETWEEN 0 AND 100)
);
