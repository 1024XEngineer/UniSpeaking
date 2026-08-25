-- Flash is explicitly selected by latency-sensitive features and is not added
-- to the global LLM route. Plus pricing is used as a conservative estimate
-- until the workspace-specific Flash billing rate is verified.
INSERT INTO ai_models (
    model_id, provider_id, display_name, capability, route_priority, billing_unit,
    input_price_per_million, output_price_per_million, character_price_per_million,
    audio_input_price_per_minute, audio_output_price_per_minute, request_price_per_call
) VALUES (
    'qwen3.5-flash', 'qwen', 'Qwen 3.5 Flash', 'LLM', NULL, 'TOKENS',
    0.8, 4.8, 0, 0, 0, 0
)
ON CONFLICT (model_id) DO NOTHING;
