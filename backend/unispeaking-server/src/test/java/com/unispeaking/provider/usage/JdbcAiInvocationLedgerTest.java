package com.unispeaking.provider.usage;

import static org.assertj.core.api.Assertions.assertThat;

import com.unispeaking.domain.vo.provider.AiCapability;
import com.unispeaking.provider.AiInvocationContext;
import com.unispeaking.provider.ProviderUsage;
import com.unispeaking.provider.config.AiModelConfiguration;
import java.math.BigDecimal;
import java.time.Instant;
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
}
