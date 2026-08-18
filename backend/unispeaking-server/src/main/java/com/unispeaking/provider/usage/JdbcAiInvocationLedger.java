package com.unispeaking.provider.usage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public final class JdbcAiInvocationLedger implements AiInvocationLedger {
	private static final Logger LOGGER = LoggerFactory.getLogger(JdbcAiInvocationLedger.class);
	private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);
	private static final BigDecimal MINUTE_SECONDS = BigDecimal.valueOf(60);

	private final JdbcTemplate jdbc;
	private final ObjectMapper objectMapper;

	public JdbcAiInvocationLedger(JdbcTemplate jdbc, ObjectMapper objectMapper) {
		this.jdbc = jdbc;
		this.objectMapper = objectMapper;
	}

	@Override
	public void record(AiInvocationAttempt attempt) {
		try {
			var context = attempt.context();
			var usage = attempt.usage();
			var model = attempt.model();
			BigDecimal cost = estimateCost(attempt);
			String pricing = objectMapper.writeValueAsString(Map.of(
					"billing_unit", model.billingUnit(),
					"input_price_per_million", model.inputPricePerMillion(),
					"output_price_per_million", model.outputPricePerMillion(),
					"character_price_per_million", model.characterPricePerMillion(),
					"audio_input_price_per_minute", model.audioInputPricePerMinute(),
					"audio_output_price_per_minute", model.audioOutputPricePerMinute(),
					"request_price_per_call", model.requestPricePerCall()));
			jdbc.update("insert into ai_model_invocations "
					+ "(invocation_id, logical_request_id, attempt_no, user_id, session_id, business_scene, route_key, "
					+ "capability, provider_id, model_id, provider_request_id, started_at, completed_at, duration_ms, "
					+ "input_tokens, output_tokens, total_tokens, input_characters, output_characters, "
					+ "audio_input_seconds, audio_output_seconds, usage_source, status, error_code, retryable, "
					+ "fallback_from_model_id, estimated_cost, price_currency, pricing_snapshot) "
					+ "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb)) "
					+ "on conflict (invocation_id) do update set completed_at=excluded.completed_at, duration_ms=excluded.duration_ms, "
					+ "audio_input_seconds=excluded.audio_input_seconds, audio_output_seconds=excluded.audio_output_seconds, "
					+ "estimated_cost=excluded.estimated_cost, pricing_snapshot=excluded.pricing_snapshot",
					attempt.invocationId(), context.logicalRequestId(), attempt.attemptNo(), uuid(context.userId()),
					context.sessionId(), context.businessScene(), context.routeKey(), attempt.capability().name(),
					model.providerId(), model.modelId(), attempt.providerRequestId(),
					Timestamp.from(attempt.startedAt()), Timestamp.from(attempt.completedAt()), attempt.durationMs(),
					usage.inputTokens(), usage.outputTokens(), usage.totalTokens(), usage.inputCharacters(),
					usage.outputCharacters(), usage.audioInputSeconds(), usage.audioOutputSeconds(), usage.source(),
					attempt.status(), attempt.errorCode(), attempt.retryable(), attempt.fallbackFromModelId(),
					cost, model.currency(), pricing);
		}
		catch (RuntimeException exception) {
			// Metering must never turn a successful provider response into a user-facing failure.
			LOGGER.error("Failed to persist AI invocation model={} requestId={}",
					attempt.model().modelId(), attempt.context().logicalRequestId(), exception);
		}
	}

	static BigDecimal estimateCost(AiInvocationAttempt attempt) {
		var usage = attempt.usage();
		var model = attempt.model();
		BigDecimal tokenCost = BigDecimal.valueOf(usage.inputTokens())
				.multiply(value(model.inputPricePerMillion())).divide(MILLION)
				.add(BigDecimal.valueOf(usage.outputTokens())
						.multiply(value(model.outputPricePerMillion())).divide(MILLION));
		BigDecimal characterCost = BigDecimal.valueOf(usage.inputCharacters() + usage.outputCharacters())
				.multiply(value(model.characterPricePerMillion())).divide(MILLION);
		BigDecimal audioCost = BigDecimal.valueOf(usage.audioInputSeconds())
				.multiply(value(model.audioInputPricePerMinute())).divide(MINUTE_SECONDS, 12, RoundingMode.HALF_UP)
				.add(BigDecimal.valueOf(usage.audioOutputSeconds())
						.multiply(value(model.audioOutputPricePerMinute())).divide(MINUTE_SECONDS, 12, RoundingMode.HALF_UP));
		BigDecimal requestCost = value(model.requestPricePerCall());
		String billingUnit = model.billingUnit() == null ? "MIXED" : model.billingUnit().trim().toUpperCase();
		BigDecimal cost = switch (billingUnit) {
			case "TOKENS" -> tokenCost;
			case "CHARACTERS" -> characterCost;
			case "AUDIO_MINUTES" -> audioCost;
			case "REQUESTS" -> requestCost;
			default -> tokenCost.add(characterCost).add(audioCost).add(requestCost);
		};
		return cost.setScale(8, RoundingMode.HALF_UP);
	}

	private static BigDecimal value(BigDecimal value) {
		return value == null ? BigDecimal.ZERO : value;
	}

	private static UUID uuid(String value) {
		try {
			return value == null ? null : UUID.fromString(value);
		}
		catch (IllegalArgumentException exception) {
			return null;
		}
	}
}
