-- Add the provider request lookup index after the usage schema extension.
CREATE INDEX IF NOT EXISTS idx_ai_model_invocations_provider_request_id
    ON ai_model_invocations(provider_request_id);
