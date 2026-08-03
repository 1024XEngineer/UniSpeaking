package com.unispeaking.infrastructure.ai.iflytek;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class IflytekScoringProviderDateTest {

	@Test
	void formatsSingleDigitDayWithTheTwoDigitsRequiredByIflytek() {
		assertEquals(
				"Sat, 01 Aug 2026 04:54:00 GMT",
				IflytekScoringProvider.formatHttpDate(
						Instant.parse("2026-08-01T04:54:00Z")));
	}

	@Test
	void preservesTwoDigitDayInIflytekSignatureDate() {
		assertEquals(
				"Tue, 11 Aug 2026 04:54:00 GMT",
				IflytekScoringProvider.formatHttpDate(
						Instant.parse("2026-08-11T04:54:00Z")));
	}
}
