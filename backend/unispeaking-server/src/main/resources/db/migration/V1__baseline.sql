-- Flyway V1: current production schema plus idempotent legacy upgrades.
CREATE TABLE IF NOT EXISTS "user" (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    nickname VARCHAR(32),
    role VARCHAR(16) NOT NULL DEFAULT 'USER',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    auth_version BIGINT NOT NULL DEFAULT 0,
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT user_role_check CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT user_status_check CHECK (status IN ('ACTIVE', 'DISABLED', 'LOCKED')),
    CONSTRAINT user_auth_version_check CHECK (auth_version >= 0)
);

ALTER TABLE "user"
ALTER COLUMN username TYPE VARCHAR(254);

CREATE TABLE IF NOT EXISTS user_preference (
    user_id UUID PRIMARY KEY,
    preferred_voice VARCHAR(64),
    preferred_ai_speech_speed VARCHAR(16) NOT NULL DEFAULT 'NATURAL',
    preferences JSONB NOT NULL DEFAULT '{}'::JSONB,
    memory_text TEXT,
    cefr_level VARCHAR(4),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON COLUMN user_preference.memory_text IS
'用户主动维护的长期档案摘要，仅包含兴趣与熟悉背景、昵称或称谓、年龄段、代词和敏感话题边界，不存储逐轮对话或会话历史摘要';

UPDATE user_preference
SET memory_text = NULL
WHERE memory_text LIKE '本次会话摘要（本地）：%';

ALTER TABLE user_preference
DROP CONSTRAINT IF EXISTS user_preference_cefr_level_check;

UPDATE user_preference
SET cefr_level = CASE cefr_level
    WHEN 'A1' THEN 'A'
    WHEN 'A2' THEN 'B'
    WHEN 'B1' THEN 'C'
    WHEN 'B2' THEN 'D'
    WHEN 'C1' THEN 'D'
    WHEN 'C2' THEN 'D'
    ELSE cefr_level
END
WHERE cefr_level IS NOT NULL;

ALTER TABLE user_preference
ADD CONSTRAINT user_preference_cefr_level_check
CHECK (cefr_level IS NULL OR cefr_level IN ('A', 'B', 'C', 'D'));

ALTER TABLE user_preference
DROP CONSTRAINT IF EXISTS user_preference_speech_speed_check;

ALTER TABLE user_preference
ADD CONSTRAINT user_preference_speech_speed_check
CHECK (preferred_ai_speech_speed IN ('SLOWER', 'MODERATE', 'NATURAL', 'FASTER'));

CREATE TABLE IF NOT EXISTS scene (
    id VARCHAR(64) PRIMARY KEY,
    user_id UUID NOT NULL,
    title VARCHAR(128) NOT NULL,
    background TEXT NOT NULL,
    ai_role TEXT NOT NULL,
    user_role TEXT NOT NULL,
    learning_goal TEXT NOT NULL,
    custom_instruction TEXT,
    success_factor JSONB NOT NULL DEFAULT '{}'::JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT scene_id_check CHECK (id ~ '^custom_[A-Za-z0-9]+$'),
    CONSTRAINT scene_title_check CHECK (BTRIM(title) <> ''),
    CONSTRAINT scene_success_factor_check
        CHECK (JSONB_TYPEOF(success_factor) = 'object')
);

CREATE INDEX IF NOT EXISTS idx_scene_user_id
ON scene (user_id);

CREATE INDEX IF NOT EXISTS idx_scene_active_user_updated_at
ON scene (user_id, updated_at DESC)
WHERE deleted_at IS NULL;

COMMENT ON TABLE scene IS
'用户创建的自定义场景定义；学习内容和练习结果由其他业务实体保存';

COMMENT ON COLUMN scene.user_id IS
'逻辑关联 user.id，不设置数据库外键';

COMMENT ON COLUMN scene.success_factor IS
'状态机用于判断场景是否成功完成的 JSON 条件对象';

COMMENT ON COLUMN scene.deleted_at IS
'软删除时间；为空表示场景有效';

CREATE OR REPLACE FUNCTION set_scene_updated_at()
RETURNS TRIGGER
AS 'BEGIN NEW.updated_at = CURRENT_TIMESTAMP; RETURN NEW; END;'
LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS scene_set_updated_at ON scene;

CREATE TRIGGER scene_set_updated_at
BEFORE UPDATE ON scene
FOR EACH ROW
EXECUTE FUNCTION set_scene_updated_at();

CREATE TABLE IF NOT EXISTS "word" (
    scene_id VARCHAR(64) NOT NULL,
    word_id VARCHAR(64) NOT NULL,
    word VARCHAR(128) NOT NULL,
    phonetic VARCHAR(128),
    translation TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT word_pk PRIMARY KEY (scene_id, word_id),
    CONSTRAINT word_id_check CHECK (word_id ~ '^word_[A-Za-z0-9]+$'),
    CONSTRAINT word_scene_id_check CHECK (scene_id ~ '^custom_[A-Za-z0-9]+$'),
    CONSTRAINT word_text_check CHECK (BTRIM(word) <> ''),
    CONSTRAINT word_translation_check CHECK (BTRIM(translation) <> '')
);

ALTER TABLE "word"
DROP CONSTRAINT IF EXISTS word_pkey;

ALTER TABLE "word"
DROP CONSTRAINT IF EXISTS word_pk;

ALTER TABLE "word"
ADD CONSTRAINT word_pk PRIMARY KEY (scene_id, word_id);

COMMENT ON TABLE "word" IS
'自定义场景的单词学习内容';

COMMENT ON COLUMN "word".scene_id IS
'逻辑关联 scene.id，不设置数据库外键';

CREATE OR REPLACE FUNCTION set_word_updated_at()
RETURNS TRIGGER
AS 'BEGIN NEW.updated_at = CURRENT_TIMESTAMP; RETURN NEW; END;'
LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS word_set_updated_at ON "word";

CREATE TRIGGER word_set_updated_at
BEFORE UPDATE ON "word"
FOR EACH ROW
EXECUTE FUNCTION set_word_updated_at();

CREATE TABLE IF NOT EXISTS phrase (
    scene_id VARCHAR(64) NOT NULL,
    phrase_id VARCHAR(64) NOT NULL,
    phrase VARCHAR(255) NOT NULL,
    phonetic VARCHAR(255),
    translation TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT phrase_pk PRIMARY KEY (scene_id, phrase_id),
    CONSTRAINT phrase_id_check CHECK (phrase_id ~ '^phrase_[A-Za-z0-9]+$'),
    CONSTRAINT phrase_scene_id_check CHECK (scene_id ~ '^custom_[A-Za-z0-9]+$'),
    CONSTRAINT phrase_text_check CHECK (BTRIM(phrase) <> ''),
    CONSTRAINT phrase_translation_check CHECK (BTRIM(translation) <> '')
);

CREATE OR REPLACE FUNCTION migrate_phrase_composite_key()
RETURNS VOID
AS 'BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = ''public''
          AND table_name = ''phrase''
          AND column_name = ''id''
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = ''public''
          AND table_name = ''phrase''
          AND column_name = ''phrase_id''
    ) THEN
        EXECUTE ''ALTER TABLE phrase RENAME COLUMN id TO phrase_id'';
    END IF;
END;'
LANGUAGE plpgsql;

SELECT migrate_phrase_composite_key();

DROP FUNCTION migrate_phrase_composite_key();

ALTER TABLE phrase
DROP CONSTRAINT IF EXISTS phrase_pkey;

ALTER TABLE phrase
DROP CONSTRAINT IF EXISTS phrase_pk;

ALTER TABLE phrase
ADD CONSTRAINT phrase_pk PRIMARY KEY (scene_id, phrase_id);

COMMENT ON TABLE phrase IS
'自定义场景的词组学习内容';

COMMENT ON COLUMN phrase.scene_id IS
'逻辑关联 scene.id，不设置数据库外键';

CREATE OR REPLACE FUNCTION set_phrase_updated_at()
RETURNS TRIGGER
AS 'BEGIN NEW.updated_at = CURRENT_TIMESTAMP; RETURN NEW; END;'
LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS phrase_set_updated_at ON phrase;

CREATE TRIGGER phrase_set_updated_at
BEFORE UPDATE ON phrase
FOR EACH ROW
EXECUTE FUNCTION set_phrase_updated_at();

CREATE TABLE IF NOT EXISTS sentence (
    scene_id VARCHAR(64) NOT NULL,
    sentence_id VARCHAR(64) NOT NULL,
    sentence TEXT NOT NULL,
    translation TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT sentence_pk PRIMARY KEY (scene_id, sentence_id),
    CONSTRAINT sentence_content_id_check
        CHECK (sentence_id ~ '^sentence_[A-Za-z0-9]+$'),
    CONSTRAINT sentence_scene_id_check
        CHECK (scene_id ~ '^custom_[A-Za-z0-9]+$'),
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
        CHECK (id ~ '^sentence_reading_[A-Za-z0-9]+$'),
    CONSTRAINT sentence_evaluation_scene_id_check
        CHECK (scene_id ~ '^custom_[A-Za-z0-9]+$'),
    CONSTRAINT sentence_evaluation_sentence_id_check
        CHECK (sentence_id ~ '^sentence_[A-Za-z0-9]+$'),
    CONSTRAINT sentence_evaluation_overall_score_check
        CHECK (overall_score BETWEEN 0 AND 100),
    CONSTRAINT sentence_evaluation_score_detail_check
        CHECK (JSONB_TYPEOF(score_detail) = 'object')
);

CREATE OR REPLACE FUNCTION migrate_sentence_evaluation_table()
RETURNS VOID
AS 'BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = ''public''
          AND table_name = ''sentence''
          AND column_name = ''id''
    ) THEN
        EXECUTE ''INSERT INTO sentence_evaluation (
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
                 ON CONFLICT (id) DO NOTHING'';
        EXECUTE ''DELETE FROM sentence AS duplicate
                 USING sentence AS retained
                 WHERE duplicate.scene_id = retained.scene_id
                   AND duplicate.sentence_id = retained.sentence_id
                   AND (duplicate.created_at, duplicate.id)
                       > (retained.created_at, retained.id)'';
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
END;'
LANGUAGE plpgsql;

SELECT migrate_sentence_evaluation_table();

DROP FUNCTION migrate_sentence_evaluation_table();

ALTER TABLE sentence
DROP CONSTRAINT IF EXISTS sentence_pk;

ALTER TABLE sentence
ADD CONSTRAINT sentence_pk PRIMARY KEY (scene_id, sentence_id);

DROP INDEX IF EXISTS idx_sentence_scene_id;

CREATE INDEX IF NOT EXISTS idx_sentence_content_id
ON sentence (sentence_id);

DROP INDEX IF EXISTS idx_sentence_scene_content_created_at;

COMMENT ON TABLE sentence IS
'自定义场景的参考句子资产，不保存朗读评分';

COMMENT ON COLUMN sentence.sentence_id IS
'参考句子的业务标识，与 scene_id 共同构成主键';

COMMENT ON COLUMN sentence.scene_id IS
'逻辑关联 scene.id，不设置数据库外键';

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

CREATE OR REPLACE FUNCTION set_sentence_updated_at()
RETURNS TRIGGER
AS 'BEGIN NEW.updated_at = CURRENT_TIMESTAMP; RETURN NEW; END;'
LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS sentence_set_updated_at ON sentence;

CREATE TRIGGER sentence_set_updated_at
BEFORE UPDATE ON sentence
FOR EACH ROW
EXECUTE FUNCTION set_sentence_updated_at();

CREATE OR REPLACE FUNCTION set_sentence_evaluation_updated_at()
RETURNS TRIGGER
AS 'BEGIN NEW.updated_at = CURRENT_TIMESTAMP; RETURN NEW; END;'
LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS sentence_evaluation_set_updated_at
ON sentence_evaluation;

CREATE TRIGGER sentence_evaluation_set_updated_at
BEFORE UPDATE ON sentence_evaluation
FOR EACH ROW
EXECUTE FUNCTION set_sentence_evaluation_updated_at();

CREATE TABLE IF NOT EXISTS session_message (
    scene_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    message_no INTEGER NOT NULL,
    owner SMALLINT NOT NULL,
    content TEXT NOT NULL,
    audio_url TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT session_message_pk
        PRIMARY KEY (session_id, message_no),
    CONSTRAINT session_message_scene_id_check
        CHECK (BTRIM(scene_id) <> ''),
    CONSTRAINT session_message_session_id_check
        CHECK (BTRIM(session_id) <> ''),
    CONSTRAINT session_message_no_check
        CHECK (message_no > 0),
    CONSTRAINT session_message_owner_check
        CHECK (owner IN (0, 1)),
    CONSTRAINT session_message_content_check
        CHECK (BTRIM(content) <> ''),
    CONSTRAINT session_message_audio_url_check
        CHECK (audio_url IS NULL OR BTRIM(audio_url) <> '')
);

CREATE INDEX IF NOT EXISTS idx_session_message_scene_id
ON session_message (scene_id);

CREATE INDEX IF NOT EXISTS idx_session_message_created_at
ON session_message (created_at DESC);

COMMENT ON TABLE session_message IS
'按消息保存自定义场景会话的最终完整转写，不保存流式 delta';

COMMENT ON COLUMN session_message.scene_id IS
'场景业务标识，不设置数据库外键';

COMMENT ON COLUMN session_message.session_id IS
'会话业务标识，不设置数据库外键';

COMMENT ON COLUMN session_message.message_no IS
'消息在当前会话中的顺序号，从 1 开始';

COMMENT ON COLUMN session_message.owner IS
'消息归属：0 表示 AI，1 表示用户';

COMMENT ON COLUMN session_message.content IS
'用户或 AI 的最终完整对话转写，不保存流式 delta';

COMMENT ON COLUMN session_message.audio_url IS
'可选的音频对象存储地址';

CREATE OR REPLACE FUNCTION set_session_message_update_at()
RETURNS TRIGGER
AS 'BEGIN NEW.update_at = CURRENT_TIMESTAMP; RETURN NEW; END;'
LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS session_message_set_update_at
ON session_message;

CREATE TRIGGER session_message_set_update_at
BEFORE UPDATE ON session_message
FOR EACH ROW
EXECUTE FUNCTION set_session_message_update_at();

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

CREATE OR REPLACE FUNCTION migrate_turn_evaluation_composite_key()
RETURNS VOID
AS 'BEGIN
    ALTER TABLE turn_evaluation
    DROP CONSTRAINT IF EXISTS turn_evaluation_session_turn_uk;
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = ''public''
          AND table_name = ''turn_evaluation''
          AND column_name = ''id''
    ) THEN
        ALTER TABLE turn_evaluation
        DROP CONSTRAINT IF EXISTS turn_evaluation_pkey;
        ALTER TABLE turn_evaluation
        DROP COLUMN id;
    END IF;
END;'
LANGUAGE plpgsql;

SELECT migrate_turn_evaluation_composite_key();

DROP FUNCTION migrate_turn_evaluation_composite_key();

ALTER TABLE turn_evaluation
DROP CONSTRAINT IF EXISTS turn_evaluation_pk;

ALTER TABLE turn_evaluation
ADD CONSTRAINT turn_evaluation_pk
PRIMARY KEY (session_id, turn_no);

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
AS 'BEGIN NEW.updated_at = CURRENT_TIMESTAMP; RETURN NEW; END;'
LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS turn_evaluation_set_updated_at
ON turn_evaluation;

CREATE TRIGGER turn_evaluation_set_updated_at
BEFORE UPDATE ON turn_evaluation
FOR EACH ROW
EXECUTE FUNCTION set_turn_evaluation_updated_at();

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

UPDATE session_evaluation AS evaluation
SET scene_id = message.scene_id
FROM (
    SELECT DISTINCT ON (session_id) session_id, scene_id
    FROM session_message
    ORDER BY session_id, message_no
) AS message
WHERE evaluation.session_id = message.session_id
  AND evaluation.scene_id IS NULL;

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
AS 'BEGIN NEW.updated_at = CURRENT_TIMESTAMP; RETURN NEW; END;'
LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS session_evaluation_set_updated_at
ON session_evaluation;

CREATE TRIGGER session_evaluation_set_updated_at
BEFORE UPDATE ON session_evaluation
FOR EACH ROW
EXECUTE FUNCTION set_session_evaluation_updated_at();
