package com.unispeaking.service.evaluation.internal.client;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unispeaking.domain.vo.ai.AiCapability;
import com.unispeaking.exception.BusinessException;
import com.unispeaking.provider.AiProviderRegistry;
import com.unispeaking.provider.AiProviderRegistry.RoutedResult;
import com.unispeaking.service.evaluation.internal.exception.EvaluationErrorCode;
import com.unispeaking.service.evaluation.internal.exception.EvaluationException;
import com.unispeaking.service.evaluation.internal.model.EndingTone;
import com.unispeaking.service.evaluation.internal.model.PronunciationAssessmentResult;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class PronunciationAssessmentClientTest {

	private static final String REFERENCE_TEXT = "Test sentence.";
	private static final byte[] AUDIO = new byte[] {1, 2, -1, 0};

	private AiProviderRegistry registry;
	private PronunciationAssessmentClient client;

	@BeforeEach
	void setUp() {
		registry = mock(AiProviderRegistry.class);
		client = new PronunciationAssessmentClient(
				registry,
				new ObjectMapper(),
				new EvaluationProviderFailureTranslator());
	}

	@Test
	void routesAudioAndMapsCompleteIflytekResponse() {
		when(registry.evaluatePronunciationRouted(
				eq(REFERENCE_TEXT),
				any(Byte[].class),
				isNull())).thenReturn(iflytekResult(successEnvelope()));

		PronunciationAssessmentResult result =
				client.evaluate(REFERENCE_TEXT, AUDIO);

		ArgumentCaptor<Byte[]> audioCaptor =
				ArgumentCaptor.forClass(Byte[].class);
		verify(registry).evaluatePronunciationRouted(
				eq(REFERENCE_TEXT),
				audioCaptor.capture(),
				isNull());
		assertAll(
				() -> assertArrayEquals(
						new Byte[] {1, 2, -1, 0},
						audioCaptor.getValue()),
				() -> assertDecimal("90.00", result.overallScore()),
				() -> assertDecimal("82.00", result.rhythmScore()),
				() -> assertDecimal("80.00", result.toneScore()),
				() -> assertDecimal("84.00", result.integrityScore()),
				() -> assertDecimal("88.00", result.pronunciationScore()),
				() -> assertDecimal("86.00", result.fluencyScore()),
				() -> assertSame(EndingTone.FALL, result.endingTone()),
				() -> assertEquals(1, result.words().size()),
				() -> assertDecimal(
						"84.00",
						result.words().get(0).pronunciationScore()),
				() -> assertEquals(
						"t",
						result.words().get(0).phonemes().get(0)
								.expectedPhoneme()),
				() -> assertEquals(
						"d",
						result.words().get(0).phonemes().get(0)
								.actualPhoneme()));
	}

	@Test
	void normalizesReferenceTextBeforeCallingProvider() {
		when(registry.evaluatePronunciationRouted(
				eq(REFERENCE_TEXT),
				any(Byte[].class),
				isNull())).thenReturn(iflytekResult(successEnvelope()));

		client.evaluate("  " + REFERENCE_TEXT + "  ", AUDIO);

		verify(registry).evaluatePronunciationRouted(
				eq(REFERENCE_TEXT),
				any(Byte[].class),
				isNull());
	}

	@Test
	void translatesRegistryConfigurationAndCallFailures() {
		when(registry.evaluatePronunciationRouted(
				eq(REFERENCE_TEXT),
				any(Byte[].class),
				isNull()))
				.thenThrow(new BusinessException(
						"IFLYTEK_SUNTONE_CREDENTIAL_MISSING",
						"secret credential detail"))
				.thenThrow(new BusinessException(
						"IFLYTEK_SUNTONE_CONNECTION_FAILED",
						"secret connection detail"));

		assertAll(
				() -> assertError(
						EvaluationErrorCode.PROVIDER_NOT_CONFIGURED,
						() -> client.evaluate(REFERENCE_TEXT, AUDIO)),
				() -> assertError(
						EvaluationErrorCode.PROVIDER_CALL_FAILED,
						() -> client.evaluate(REFERENCE_TEXT, AUDIO)));
	}

	@Test
	void rejectsIncompleteOrInconsistentRouteResults() {
		assertAll(
				() -> assertRouteError(
						null,
						EvaluationErrorCode.PROVIDER_RESPONSE_INCOMPLETE),
				() -> assertRouteError(
						new RoutedResult<>(
								"iflytek-suntone",
								null,
								AiCapability.SCORING,
								successEnvelope()),
						EvaluationErrorCode.PROVIDER_RESPONSE_INCOMPLETE),
				() -> assertRouteError(
						iflytekResult(" "),
						EvaluationErrorCode.PROVIDER_RESPONSE_INCOMPLETE),
				() -> assertRouteError(
						new RoutedResult<>(
								"iflytek-suntone",
								"iflytek",
								AiCapability.LLM,
								successEnvelope()),
						EvaluationErrorCode.PROVIDER_RESPONSE_INVALID),
				() -> assertRouteError(
						new RoutedResult<>(
								"future-model",
								"future-vendor",
								AiCapability.SCORING,
								successEnvelope()),
						EvaluationErrorCode.PROVIDER_NOT_CONFIGURED));
	}

	@Test
	void preservesIflytekParsingErrors() {
		when(registry.evaluatePronunciationRouted(
				eq(REFERENCE_TEXT),
				any(Byte[].class),
				isNull())).thenReturn(iflytekResult("[]"));

		assertError(
				EvaluationErrorCode.PROVIDER_RESPONSE_INVALID,
				() -> client.evaluate(REFERENCE_TEXT, AUDIO));
	}

	@Test
	void rejectsInvalidInputBeforeCallingProvider() {
		assertAll(
				() -> assertError(
						EvaluationErrorCode.INVALID_REQUEST,
						() -> client.evaluate(null, AUDIO)),
				() -> assertError(
						EvaluationErrorCode.INVALID_REQUEST,
						() -> client.evaluate(" ", AUDIO)),
				() -> assertError(
						EvaluationErrorCode.AUDIO_REQUIRED,
						() -> client.evaluate(REFERENCE_TEXT, null)),
				() -> assertError(
						EvaluationErrorCode.AUDIO_REQUIRED,
						() -> client.evaluate(REFERENCE_TEXT, new byte[0])));
		verify(registry, never()).evaluatePronunciationRouted(
				any(),
				any(),
				any());
	}

	private void assertRouteError(
			RoutedResult<String> routedResult,
			EvaluationErrorCode expectedErrorCode) {
		when(registry.evaluatePronunciationRouted(
				eq(REFERENCE_TEXT),
				any(Byte[].class),
				isNull())).thenReturn(routedResult);

		assertError(
				expectedErrorCode,
				() -> client.evaluate(REFERENCE_TEXT, AUDIO));
	}

	private EvaluationException assertError(
			EvaluationErrorCode expectedErrorCode,
			Runnable invocation) {
		EvaluationException exception = assertThrows(
				EvaluationException.class,
				invocation::run);
		assertSame(expectedErrorCode, exception.errorCode());
		assertEquals(
				expectedErrorCode.defaultMessage(),
				exception.getMessage());
		return exception;
	}

	private static RoutedResult<String> iflytekResult(String response) {
		return new RoutedResult<>(
				"iflytek-suntone",
				"iflytek",
				AiCapability.SCORING,
				response);
	}

	private static String successEnvelope() {
		String encodedResult = Base64.getEncoder().encodeToString(
				completeResult().getBytes(StandardCharsets.UTF_8));
		return """
				{
				  "header": {
				    "code": 0,
				    "message": "success",
				    "status": 2
				  },
				  "payload": {
				    "result": {
				      "status": 2,
				      "text": "%s"
				    }
				  }
				}
				""".formatted(encodedResult);
	}

	private static String completeResult() {
		return """
				{
				  "eof": 1,
				  "result": {
				    "overall": 90,
				    "rhythm": 82,
				    "tone": 80,
				    "integrity": 84,
				    "pronunciation": 88,
				    "fluency": 86,
				    "rear_tone": "fall",
				    "words": [
				      {
				        "charType": 0,
				        "word": "test",
				        "readType": 0,
				        "scores": {
				          "overall": 86,
				          "pronunciation": 84,
				          "prominence": 1
				        },
				        "phonemes": [
				          {
				            "phone": "t",
				            "phoneme": "d",
				            "pronunciation": 82,
				            "span": {"start": 0, "end": 12}
				          }
				        ]
				      }
				    ]
				  }
				}
				""";
	}

	private static void assertDecimal(
			String expected,
			BigDecimal actual) {
		assertEquals(new BigDecimal(expected), actual);
	}
}
