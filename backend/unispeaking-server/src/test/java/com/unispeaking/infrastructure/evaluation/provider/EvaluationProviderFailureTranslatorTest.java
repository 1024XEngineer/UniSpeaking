package com.unispeaking.infrastructure.evaluation.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.common.exception.evaluation.EvaluationErrorCode;
import com.unispeaking.common.exception.evaluation.EvaluationException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class EvaluationProviderFailureTranslatorTest {

	private final EvaluationProviderFailureTranslator translator =
			new EvaluationProviderFailureTranslator();

	@ParameterizedTest
	@MethodSource("notConfiguredCodes")
	void translatesMissingConfigurationToStableEvaluationError(String providerCode) {
		BusinessException providerException =
				new BusinessException(providerCode, "sensitive provider detail");

		EvaluationException result = translator.translate(providerException);

		assertEquals(EvaluationErrorCode.PROVIDER_NOT_CONFIGURED, result.errorCode());
		assertEquals(
				EvaluationErrorCode.PROVIDER_NOT_CONFIGURED.defaultMessage(),
				result.getMessage());
		assertSame(providerException, result.getCause());
	}

	@ParameterizedTest
	@MethodSource("providerCallFailureCodes")
	void translatesOtherProviderFailuresToStableEvaluationError(String providerCode) {
		BusinessException providerException =
				new BusinessException(providerCode, "sensitive provider detail");

		EvaluationException result = translator.translate(providerException);

		assertEquals(EvaluationErrorCode.PROVIDER_CALL_FAILED, result.errorCode());
		assertEquals(
				EvaluationErrorCode.PROVIDER_CALL_FAILED.defaultMessage(),
				result.getMessage());
		assertSame(providerException, result.getCause());
	}

	@Test
	void treatsNullAndBlankProviderCodesAsCallFailures() {
		assertEquals(
				EvaluationErrorCode.PROVIDER_CALL_FAILED,
				translator.translate(new BusinessException(null, "detail")).errorCode());
		assertEquals(
				EvaluationErrorCode.PROVIDER_CALL_FAILED,
				translator.translate(new BusinessException("  ", "detail")).errorCode());
	}

	@Test
	void preservesEvaluationExceptionsWithoutWrappingThemAgain() {
		EvaluationException evaluationException = new EvaluationException(
				EvaluationErrorCode.PROVIDER_RESPONSE_INVALID);

		assertSame(evaluationException, translator.translate(evaluationException));
	}

	@Test
	void rejectsNullExceptionInput() {
		assertThrows(NullPointerException.class, () -> translator.translate(null));
	}

	private static Stream<String> notConfiguredCodes() {
		return Stream.of(
				"AI_PROVIDER_ROUTE_NOT_FOUND",
				"AI_PROVIDER_NOT_FOUND",
				"AI_PROVIDER_CAPABILITY_NOT_CONFIGURED",
				"QWEN_LLM_CREDENTIAL_MISSING",
				"DEEPSEEK_LLM_CREDENTIAL_MISSING",
				"IFLYTEK_ISE_CREDENTIAL_MISSING");
	}

	private static Stream<String> providerCallFailureCodes() {
		return Stream.of(
				"AI_PROVIDER_ROUTE_EXHAUSTED",
				"QWEN_LLM_IO_ERROR",
				"DEEPSEEK_LLM_TIMEOUT",
				"IFLYTEK_ISE_CONNECTION_FAILED",
				"QWEN_LLM_RESPONSE_TOO_LARGE",
				"UNKNOWN_PROVIDER_FAILURE");
	}
}
