package com.unispeaking.service.evaluation.internal.client;

import com.unispeaking.exception.BusinessException;
import com.unispeaking.service.evaluation.internal.exception.EvaluationErrorCode;
import com.unispeaking.service.evaluation.internal.exception.EvaluationException;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 将 AI Provider 边界抛出的异常转换为评分模块稳定错误码。
 *
 * <p>转换后的异常只暴露评分模块预设的安全消息；原异常仅作为服务端诊断
 * cause 保留，不能把供应商响应、凭据或底层连接信息拼接到对外消息。</p>
 */
@Component
public final class EvaluationProviderFailureTranslator {

	private static final String CREDENTIAL_MISSING_SUFFIX =
			"_CREDENTIAL_MISSING";

	private static final Set<String> NOT_CONFIGURED_CODES = Set.of(
			"AI_PROVIDER_ROUTE_NOT_FOUND",
			"AI_PROVIDER_NOT_FOUND",
			"AI_PROVIDER_CAPABILITY_NOT_CONFIGURED");

	/**
	 * 转换 Provider 业务异常，并避免重复包装评分模块自身的异常。
	 *
	 * @param exception Provider 或评分解析边界抛出的业务异常
	 * @return 可由评分服务安全向上传递的评分异常
	 */
	public EvaluationException translate(BusinessException exception) {
		BusinessException requiredException = Objects.requireNonNull(
				exception,
				"exception must not be null");
		if (requiredException instanceof EvaluationException evaluationException) {
			return evaluationException;
		}

		EvaluationErrorCode errorCode = isNotConfigured(requiredException.code())
				? EvaluationErrorCode.PROVIDER_NOT_CONFIGURED
				: EvaluationErrorCode.PROVIDER_CALL_FAILED;
		return new EvaluationException(errorCode, null, requiredException);
	}

	private boolean isNotConfigured(String providerCode) {
		if (providerCode == null || providerCode.isBlank()) {
			return false;
		}
		return NOT_CONFIGURED_CODES.contains(providerCode)
				|| providerCode.endsWith(CREDENTIAL_MISSING_SUFFIX);
	}
}
