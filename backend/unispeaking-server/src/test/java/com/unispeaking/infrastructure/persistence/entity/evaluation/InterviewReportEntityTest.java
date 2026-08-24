package com.unispeaking.infrastructure.persistence.entity.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InterviewReportEntityTest {

	@Test
	void retainsNullableAssessmentFieldsAndAsyncStateColumns() {
		InterviewReportEntity entity = new InterviewReportEntity();
		UUID userId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.parse("2026-08-01T00:00:00Z");

		entity.setSessionId("session-1");
		entity.setSceneId("scene-1");
		entity.setUserId(userId);
		entity.setStatus("PROCESSING");
		entity.setRetryCount(0);
		entity.setFailureReason(null);
		entity.setOverallScore(new BigDecimal("8.5"));
		entity.setCreatedAt(now);
		entity.setUpdatedAt(now.plusSeconds(1));

		assertEquals("session-1", entity.getSessionId());
		assertEquals("scene-1", entity.getSceneId());
		assertEquals(userId, entity.getUserId());
		assertEquals("PROCESSING", entity.getStatus());
		assertEquals(0, entity.getRetryCount());
		assertNull(entity.getFailureReason());
		assertEquals(new BigDecimal("8.5"), entity.getOverallScore());
		assertEquals(now.plusSeconds(1), entity.getUpdatedAt());
	}

	@Test
	void supportsCompletedScoresAndFailureTransitionValues() {
		InterviewReportEntity entity = new InterviewReportEntity();
		entity.setStatus("FAILED");
		entity.setFailureReason("PROVIDER_TIMEOUT");
		entity.setRetryCount(3);
		entity.setFluencyScore(new BigDecimal("7.0"));
		entity.setPronunciationIntelligibilityScore(new BigDecimal("6.5"));
		entity.setLogicCoherenceScore(new BigDecimal("7.5"));
		entity.setGrammarControlScore(new BigDecimal("6.0"));
		entity.setVocabularyExpressionScore(new BigDecimal("7.0"));

		assertEquals("FAILED", entity.getStatus());
		assertEquals("PROVIDER_TIMEOUT", entity.getFailureReason());
		assertEquals(3, entity.getRetryCount());
		assertEquals(new BigDecimal("7.0"), entity.getFluencyScore());
		assertEquals(new BigDecimal("6.5"), entity.getPronunciationIntelligibilityScore());
		assertEquals(new BigDecimal("7.5"), entity.getLogicCoherenceScore());
		assertEquals(new BigDecimal("6.0"), entity.getGrammarControlScore());
		assertEquals(new BigDecimal("7.0"), entity.getVocabularyExpressionScore());
	}
}
