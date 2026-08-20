-- AI Provider configuration and usage ledger for databases that already ran V1.
-- This migration intentionally does not read, update, or delete any user-owned
-- table. It is safe to run when these tables are already present.

CREATE TABLE IF NOT EXISTS ai_providers (
    provider_id VARCHAR(64) PRIMARY KEY,
    display_name VARCHAR(128) NOT NULL,
    adapter_type VARCHAR(64) NOT NULL,
    base_url VARCHAR(1000),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    connect_timeout_ms INTEGER NOT NULL DEFAULT 10000,
    read_timeout_ms INTEGER NOT NULL DEFAULT 60000,
    config_version BIGINT NOT NULL DEFAULT 1,
    secret_ciphertext TEXT,
    secret_fingerprint VARCHAR(32),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ai_provider_id_check CHECK (provider_id ~ '^[a-z0-9][a-z0-9_-]*$'),
    CONSTRAINT ai_provider_timeouts_check CHECK (connect_timeout_ms > 0 AND read_timeout_ms > 0),
    CONSTRAINT ai_provider_secret_check CHECK (
        (secret_ciphertext IS NULL AND secret_fingerprint IS NULL)
        OR (secret_ciphertext IS NOT NULL AND secret_fingerprint IS NOT NULL))
);

CREATE TABLE IF NOT EXISTS ai_models (
    model_id VARCHAR(128) PRIMARY KEY,
    provider_id VARCHAR(64) NOT NULL REFERENCES ai_providers(provider_id),
    display_name VARCHAR(128) NOT NULL,
    capability VARCHAR(32) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    route_priority INTEGER,
    billing_unit VARCHAR(32) NOT NULL DEFAULT 'TOKENS',
    input_price_per_million NUMERIC(20,8) NOT NULL DEFAULT 0,
    output_price_per_million NUMERIC(20,8) NOT NULL DEFAULT 0,
    character_price_per_million NUMERIC(20,8) NOT NULL DEFAULT 0,
    audio_input_price_per_minute NUMERIC(20,8) NOT NULL DEFAULT 0,
    audio_output_price_per_minute NUMERIC(20,8) NOT NULL DEFAULT 0,
    request_price_per_call NUMERIC(20,8) NOT NULL DEFAULT 0,
    price_currency VARCHAR(8) NOT NULL DEFAULT 'CNY',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ai_model_capability_check CHECK (capability IN ('REALTIME','LLM','SCORING','TTS','TRANSCRIPTION')),
    CONSTRAINT ai_model_billing_unit_check CHECK (billing_unit IN ('TOKENS','AUDIO_MINUTES','CHARACTERS','REQUESTS','MIXED')),
    CONSTRAINT ai_model_route_priority_check CHECK (route_priority IS NULL OR route_priority >= 0),
    CONSTRAINT ai_model_route_priority_unique UNIQUE (capability, route_priority),
    CONSTRAINT ai_model_prices_check CHECK (
        input_price_per_million >= 0 AND output_price_per_million >= 0 AND character_price_per_million >= 0
        AND audio_input_price_per_minute >= 0 AND audio_output_price_per_minute >= 0
        AND request_price_per_call >= 0)
);

ALTER TABLE ai_models
    ADD COLUMN IF NOT EXISTS request_price_per_call NUMERIC(20,8) NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_ai_models_provider
    ON ai_models(provider_id, capability, enabled);
CREATE INDEX IF NOT EXISTS idx_ai_models_route
    ON ai_models(capability, route_priority)
    WHERE route_priority IS NOT NULL;

CREATE TABLE IF NOT EXISTS ai_model_invocations (
    invocation_id UUID PRIMARY KEY,
    logical_request_id UUID NOT NULL,
    attempt_no INTEGER NOT NULL,
    user_id UUID,
    session_id VARCHAR(64),
    business_scene VARCHAR(64) NOT NULL DEFAULT 'unspecified',
    route_key VARCHAR(64) NOT NULL DEFAULT 'default',
    capability VARCHAR(32) NOT NULL,
    provider_id VARCHAR(64) NOT NULL,
    model_id VARCHAR(128) NOT NULL,
    provider_request_id VARCHAR(256),
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL,
    duration_ms BIGINT NOT NULL,
    first_token_latency_ms BIGINT,
    input_tokens BIGINT NOT NULL DEFAULT 0,
    output_tokens BIGINT NOT NULL DEFAULT 0,
    total_tokens BIGINT NOT NULL DEFAULT 0,
    input_characters BIGINT NOT NULL DEFAULT 0,
    output_characters BIGINT NOT NULL DEFAULT 0,
    audio_input_seconds NUMERIC(16,3) NOT NULL DEFAULT 0,
    audio_output_seconds NUMERIC(16,3) NOT NULL DEFAULT 0,
    usage_source VARCHAR(16) NOT NULL DEFAULT 'ESTIMATED',
    status VARCHAR(16) NOT NULL,
    error_code VARCHAR(128),
    retryable BOOLEAN NOT NULL DEFAULT FALSE,
    fallback_from_model_id VARCHAR(128),
    estimated_cost NUMERIC(20,8) NOT NULL DEFAULT 0,
    price_currency VARCHAR(8) NOT NULL DEFAULT 'CNY',
    pricing_snapshot JSONB NOT NULL DEFAULT '{}'::JSONB,
    CONSTRAINT ai_invocation_attempt_check CHECK (attempt_no > 0),
    CONSTRAINT ai_invocation_duration_check CHECK (duration_ms >= 0),
    CONSTRAINT ai_invocation_status_check CHECK (status IN ('SUCCEEDED','FAILED')),
    CONSTRAINT ai_invocation_usage_source_check CHECK (usage_source IN ('PROVIDER','ESTIMATED','OFFICIAL','NONE'))
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_ai_invocation_attempt
    ON ai_model_invocations(logical_request_id, attempt_no);
CREATE INDEX IF NOT EXISTS idx_ai_invocation_user_time
    ON ai_model_invocations(user_id, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_ai_invocation_model_time
    ON ai_model_invocations(model_id, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_ai_invocation_session
    ON ai_model_invocations(session_id, started_at DESC);

INSERT INTO ai_providers (provider_id, display_name, adapter_type) VALUES
    ('qwen', '通义千问', 'qwen'),
    ('deepseek', 'DeepSeek', 'deepseek'),
    ('qiniu-maas', '七牛云 MaaS', 'qiniu-maas'),
    ('qiniu', '七牛云 RTI', 'qiniu'),
    ('iflytek', '讯飞开放平台', 'iflytek'),
    ('aliyun', '阿里云语音', 'aliyun'),
    ('minimax', 'MiniMax', 'minimax'),
    ('doubao', '豆包语音', 'doubao')
ON CONFLICT (provider_id) DO NOTHING;

-- Older baseline seeds assigned LLM priorities 10 and 20 to the two direct
-- providers. Move only that untouched seed layout to make Qiniu MaaS primary;
-- custom administrator routing is otherwise preserved.
DO $migration$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM ai_models WHERE model_id = 'qwen/qwen3.5-plus'
    ) AND EXISTS (
        SELECT 1 FROM ai_models
        WHERE model_id = 'qwen3.5-plus' AND capability = 'LLM' AND route_priority = 10
    ) AND EXISTS (
        SELECT 1 FROM ai_models
        WHERE model_id = 'deepseek-v4-flash' AND capability = 'LLM' AND route_priority = 20
    ) THEN
        UPDATE ai_models SET route_priority = 30
        WHERE model_id = 'deepseek-v4-flash' AND capability = 'LLM' AND route_priority = 20;
        UPDATE ai_models SET route_priority = 20
        WHERE model_id = 'qwen3.5-plus' AND capability = 'LLM' AND route_priority = 10;
    END IF;
END
$migration$;

INSERT INTO ai_models (
    model_id, provider_id, display_name, capability, route_priority, billing_unit,
    input_price_per_million, output_price_per_million, character_price_per_million,
    audio_input_price_per_minute, audio_output_price_per_minute, request_price_per_call
) VALUES
    ('qwen3.5-omni-flash-realtime', 'qwen', 'Qwen Realtime Flash', 'REALTIME', 20, 'MIXED', 3.3, 20, 0, 0.01134, 0.08025, 0),
    ('qwen3.5-omni-plus-realtime', 'qiniu', 'Qiniu Qwen Realtime Plus', 'REALTIME', 10, 'AUDIO_MINUTES', 0, 0, 0, 0.0336, 0.225, 0),
    ('qwen/qwen3.5-plus', 'qiniu-maas', 'Qiniu MaaS Qwen 3.5 Plus', 'LLM', 10, 'TOKENS', 0.8, 4.8, 0, 0, 0, 0),
    ('deepseek/deepseek-v4-flash', 'qiniu-maas', 'Qiniu MaaS DeepSeek V4 Flash', 'LLM', NULL, 'TOKENS', 3, 9, 0, 0, 0, 0),
    ('qwen3.5-plus', 'qwen', 'Qwen 3.5 Plus', 'LLM', 20, 'TOKENS', 0.8, 4.8, 0, 0, 0, 0),
    ('deepseek-v4-flash', 'deepseek', 'DeepSeek V4 Flash', 'LLM', 30, 'TOKENS', 3, 9, 0, 0, 0, 0),
    ('qwen3-asr-flash', 'qwen', 'Qwen ASR Flash', 'TRANSCRIPTION', 10, 'AUDIO_MINUTES', 0, 0, 0, 0.0132, 0, 0),
    ('volc.bigasr.auc_turbo', 'doubao', 'Doubao BigASR Turbo', 'TRANSCRIPTION', 20, 'AUDIO_MINUTES', 0, 0, 0, 0.075, 0, 0),
    ('iflytek-suntone', 'iflytek', 'Iflytek Suntone', 'SCORING', 10, 'REQUESTS', 0, 0, 0, 0, 0, 0.005),
    ('qwen3-tts-flash', 'qwen', 'Qwen TTS Flash', 'TTS', 10, 'CHARACTERS', 0, 0, 80, 0, 0, 0),
    ('cosyvoice-v3-flash', 'aliyun', 'CosyVoice V3 Flash', 'TTS', 20, 'CHARACTERS', 0, 0, 100, 0, 0, 0),
    ('speech-2.8-hd', 'minimax', 'MiniMax Speech 2.8 HD', 'TTS', 30, 'CHARACTERS', 0, 0, 350, 0, 0, 0)
ON CONFLICT (model_id) DO NOTHING;
