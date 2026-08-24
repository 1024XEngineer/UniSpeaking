package com.unispeaking.provider.usage;

import static org.assertj.core.api.Assertions.assertThat;

import com.unispeaking.domain.vo.provider.AiCapability;
import com.unispeaking.provider.AiInvocationContext;
import com.unispeaking.provider.ProviderUsage;
import com.unispeaking.provider.config.AiModelConfiguration;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.mockito.Mockito;
import org.junit.jupiter.api.Test;

class JdbcAiInvocationLedgerTest {

	@Test
	void calculatesTokenCharacterAndAudioPricesFromTheInvocationSnapshot() {
		AiModelConfiguration model = new AiModelConfiguration(
				"mixed-model", "provider", "Mixed", AiCapability.REALTIME, true, "MIXED",
				new BigDecimal("2"), new BigDecimal("4"), new BigDecimal("0.5"),
				new BigDecimal("3"), new BigDecimal("8"), BigDecimal.ZERO, "CNY");
		AiInvocationAttempt attempt = new AiInvocationAttempt(
				null, AiInvocationContext.anonymous("test"), 1, AiCapability.REALTIME, model,
				null, Instant.EPOCH, Instant.EPOCH.plusSeconds(2), 2_000,
				new ProviderUsage(1_000_000, 500_000, 1_500_000, 500_000, 120, 30, "PROVIDER"),
				"SUCCEEDED", null, false, null);

		assertThat(JdbcAiInvocationLedger.estimateCost(attempt)).isEqualByComparingTo("15.00000000");
	}

	@Test
	void calculatesPerCallPricingForRequestBilledModels() {
		AiModelConfiguration model = new AiModelConfiguration(
				"request-model", "provider", "Request", AiCapability.SCORING, true, "REQUESTS",
				BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN,
				new BigDecimal("0.005"), "CNY");
		AiInvocationAttempt attempt = new AiInvocationAttempt(
				null, AiInvocationContext.anonymous("test"), 1, AiCapability.SCORING, model,
				null, Instant.EPOCH, Instant.EPOCH.plusMillis(100), 100,
				new ProviderUsage(1_000, 1_000, 1_000, 1_000, 60, 60, "PROVIDER"),
				"SUCCEEDED", null, false, null);

		assertThat(JdbcAiInvocationLedger.estimateCost(attempt)).isEqualByComparingTo("0.00500000");
	}

	@Test
	void defaultsMissingUsageAndPricingToZeroAndSupportsEachBillingUnit() {
		AiInvocationContext context = AiInvocationContext.create(
				"not-a-uuid", " session-1 ", " billing ");
		AiModelConfiguration base = new AiModelConfiguration(
				"m", "p", "Model", AiCapability.LLM, true, "TOKENS",
				new BigDecimal("2"), new BigDecimal("3"), new BigDecimal("5"),
				new BigDecimal("7"), new BigDecimal("11"), new BigDecimal("13"), "CNY");
		AiInvocationAttempt noUsage = new AiInvocationAttempt(null, context, 1,
				AiCapability.LLM, base, null, Instant.EPOCH, Instant.EPOCH, 0,
				null, "FAILED", "ERR", true, null);
		assertThat(noUsage.invocationId()).isNotNull();
		assertThat(noUsage.usage().source()).isEqualTo("NONE");
		assertThat(JdbcAiInvocationLedger.estimateCost(noUsage)).isEqualByComparingTo("0.00000000");

		for (String unit : new String[] {"CHARACTERS", "AUDIO_MINUTES", "REQUESTS", "UNKNOWN"}) {
			AiModelConfiguration model = new AiModelConfiguration("m", "p", "Model",
					AiCapability.LLM, true, unit, BigDecimal.ONE, BigDecimal.ONE,
					BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("2"), "CNY");
			AiInvocationAttempt attempt = new AiInvocationAttempt(UUID.randomUUID(), context, 1,
					AiCapability.LLM, model, null, Instant.EPOCH, Instant.EPOCH, 0,
					new ProviderUsage(1, 2, 3, 4, 60, 120, "provider"), "OK", null, false, null);
			assertThat(JdbcAiInvocationLedger.estimateCost(attempt)).isNotNull();
		}
	}

	@Test
	void recordSwallowsMeteringFailures() throws Exception {
		var jdbc = Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);
		var mapper = Mockito.mock(tools.jackson.databind.ObjectMapper.class);
		Mockito.when(mapper.writeValueAsString(Mockito.any())).thenReturn("{}");
		Mockito.doThrow(new IllegalStateException("db down")).when(jdbc)
				.update(Mockito.anyString(), Mockito.any(Object[].class));
		AiModelConfiguration model = new AiModelConfiguration("m", "p", "Model",
				AiCapability.LLM, true, "REQUESTS", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
				new BigDecimal("1"), "CNY");
		AiInvocationAttempt attempt = new AiInvocationAttempt(null,
				AiInvocationContext.anonymous("test"), 1, AiCapability.LLM, model, null,
				Instant.EPOCH, Instant.EPOCH, 0, null, "OK", null, false, null);
		new JdbcAiInvocationLedger(jdbc, mapper).record(attempt);
	}
}
