CREATE INDEX IF NOT EXISTS idx_ai_model_invocations_provider_request_id
    ON ai_model_invocations(provider_request_id);
