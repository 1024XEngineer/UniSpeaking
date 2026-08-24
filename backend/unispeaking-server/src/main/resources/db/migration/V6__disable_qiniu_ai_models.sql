-- Qiniu object storage remains enabled. This migration only disables Qiniu AI
-- providers and removes their models from runtime routing.
UPDATE ai_providers
SET enabled = FALSE,
    updated_at = CURRENT_TIMESTAMP
WHERE provider_id IN ('qiniu', 'qiniu-maas');

UPDATE ai_models
SET enabled = FALSE,
    route_priority = NULL,
    updated_at = CURRENT_TIMESTAMP
WHERE provider_id IN ('qiniu', 'qiniu-maas');

-- Promote the direct providers only when their priorities still use the
-- project defaults. Administrator-defined routing remains untouched.
UPDATE ai_models
SET route_priority = 10,
    updated_at = CURRENT_TIMESTAMP
WHERE model_id = 'qwen3.5-omni-flash-realtime'
  AND route_priority = 20;

UPDATE ai_models
SET route_priority = 10,
    updated_at = CURRENT_TIMESTAMP
WHERE model_id = 'qwen3.5-plus'
  AND route_priority = 20;

UPDATE ai_models
SET route_priority = 20,
    updated_at = CURRENT_TIMESTAMP
WHERE model_id = 'deepseek-v4-flash'
  AND route_priority = 30;
