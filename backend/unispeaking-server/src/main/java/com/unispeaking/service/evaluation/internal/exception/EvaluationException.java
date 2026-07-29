package com.unispeaking.service.evaluation.internal.exception;

import com.unispeaking.exception.BusinessException;
import java.util.Objects;

/**
 * 评分模块业务异常。
 *
 * <p>继承项目现有 {@link BusinessException}，从而复用统一的 API 错误响应。
 * 异常消息只能使用经过筛选的业务描述，不能携带 Provider 原始响应、Token 或数据库连接信息。</p>
 */
public final class EvaluationException extends BusinessException {

	private final EvaluationErrorCode errorCode;

	/**
	 * 使用错误码预设的安全消息创建异常。
	 */
	public EvaluationException(EvaluationErrorCode errorCode) {
		this(errorCode, null, null);
	}

	/**
	 * 使用调用方提供的安全业务消息创建异常。
	 */
	public EvaluationException(EvaluationErrorCode errorCode, String message) {
		this(errorCode, message, null);
	}

	/**
	 * 创建保留底层原因的评分异常。
	 *
	 * <p>cause 仅用于服务端诊断，不会拼接到对外错误消息中。</p>
	 */
	public EvaluationException(
			EvaluationErrorCode errorCode,
			String message,
			Throwable cause) {
		super(required(errorCode).code(), resolveMessage(errorCode, message));
		this.errorCode = errorCode;
		if (cause != null) {
			initCause(cause);
		}
	}

	/**
	 * 返回内部使用的强类型错误码。
	 */
	public EvaluationErrorCode errorCode() {
		return errorCode;
	}

	private static EvaluationErrorCode required(EvaluationErrorCode errorCode) {
		return Objects.requireNonNull(errorCode, "errorCode must not be null");
	}

	private static String resolveMessage(
			EvaluationErrorCode errorCode,
			String message) {
		EvaluationErrorCode requiredCode = required(errorCode);
		if (message == null || message.isBlank()) {
			return requiredCode.defaultMessage();
		}
		return message.trim();
	}
}
