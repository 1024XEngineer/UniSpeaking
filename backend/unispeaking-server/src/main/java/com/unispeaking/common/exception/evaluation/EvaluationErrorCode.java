package com.unispeaking.common.exception.evaluation;

/**
 * 评分模块统一错误码。
 *
 * <p>枚举名称供评分模块内部引用，{@link #code()} 返回值是对外稳定的业务错误码。
 * 已发布的错误码只能新增，不能修改既有值。</p>
 */
public enum EvaluationErrorCode {

	// 请求内容不满足评分入口的基础要求。
	INVALID_REQUEST(
			"EVALUATION_INVALID_REQUEST",
			"Evaluation request is invalid"),
	TRANSCRIPT_REQUIRED(
			"EVALUATION_TRANSCRIPT_REQUIRED",
			"Transcript must contain at least one valid English word"),

	// 音频缺失、格式不受支持或 WAV 内容不符合供应商要求。
	AUDIO_REQUIRED(
			"EVALUATION_AUDIO_REQUIRED",
			"Audio is required"),
	AUDIO_UNSUPPORTED(
			"EVALUATION_AUDIO_UNSUPPORTED",
			"Only WAV audio is supported"),
	AUDIO_INVALID(
			"EVALUATION_AUDIO_INVALID",
			"Audio is not a valid 16 kHz mono 16-bit PCM WAV file"),

	// 评分依赖的句子、会话或会话状态不满足要求。
	SENTENCE_NOT_FOUND(
			"EVALUATION_SENTENCE_NOT_FOUND",
			"Sentence was not found"),
	SESSION_NOT_FOUND(
			"EVALUATION_SESSION_NOT_FOUND",
			"Practice session was not found"),
	SESSION_STATE_INVALID(
			"EVALUATION_SESSION_STATE_INVALID",
			"Practice session state does not allow evaluation"),

	// AI Provider 未配置、调用失败、拒识或返回内容无法安全使用。
	PROVIDER_NOT_CONFIGURED(
			"EVALUATION_PROVIDER_NOT_CONFIGURED",
			"Evaluation provider is not configured"),
	PROVIDER_CALL_FAILED(
			"EVALUATION_PROVIDER_CALL_FAILED",
			"Evaluation provider call failed"),
	PROVIDER_REJECTED(
			"EVALUATION_PROVIDER_REJECTED",
			"Evaluation provider rejected the input"),
	PROVIDER_RESPONSE_INVALID(
			"EVALUATION_PROVIDER_RESPONSE_INVALID",
			"Evaluation provider response is invalid"),
	PROVIDER_RESPONSE_INCOMPLETE(
			"EVALUATION_PROVIDER_RESPONSE_INCOMPLETE",
			"Evaluation provider response is incomplete"),

	// Prompt 模板缺失或结构损坏，无法构造受控的模型输入。
	PROMPT_TEMPLATE_INVALID(
			"EVALUATION_PROMPT_TEMPLATE_INVALID",
			"Evaluation prompt template is invalid"),

	// 整场报告没有可评分内容，或持久化结果缺失、不完整。
	NO_SCORABLE_UTTERANCES(
			"EVALUATION_NO_SCORABLE_UTTERANCES",
			"Conversation has no scorable utterances"),
	RESULT_NOT_FOUND(
			"EVALUATION_RESULT_NOT_FOUND",
			"Conversation evaluation result was not found"),
	RESULT_INCOMPLETE(
			"EVALUATION_RESULT_INCOMPLETE",
			"Conversation evaluation result is incomplete"),

	// 数据库访问失败时隐藏底层实现和连接信息。
	PERSISTENCE_FAILED(
			"EVALUATION_PERSISTENCE_FAILED",
			"Evaluation persistence operation failed");

	private final String code;
	private final String defaultMessage;

	EvaluationErrorCode(String code, String defaultMessage) {
		this.code = code;
		this.defaultMessage = defaultMessage;
	}

	/**
	 * 返回对外稳定的业务错误码。
	 */
	public String code() {
		return code;
	}

	/**
	 * 返回不包含供应商响应、数据库信息等敏感内容的默认消息。
	 */
	public String defaultMessage() {
		return defaultMessage;
	}
}
