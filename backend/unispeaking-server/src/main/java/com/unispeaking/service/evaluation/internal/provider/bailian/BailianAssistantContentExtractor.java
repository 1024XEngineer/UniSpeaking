package com.unispeaking.service.evaluation.internal.provider.bailian;

import com.unispeaking.service.evaluation.internal.exception.EvaluationErrorCode;
import com.unispeaking.service.evaluation.internal.exception.EvaluationException;
import java.util.Objects;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 从百炼完整 LLM 响应中严格提取唯一的 assistant content。
 *
 * <p>本类只处理供应商响应 envelope，不解析 content 中的评价 JSON，也不会
 * 记录或写入异常任何原始响应内容。</p>
 */
public final class BailianAssistantContentExtractor {

	private static final String EXPECTED_FINISH_REASON = "stop";
	private static final String EXPECTED_MESSAGE_ROLE = "assistant";

	private final ObjectMapper objectMapper;

	/**
	 * 创建使用项目 Jackson 3 配置的响应提取器。
	 *
	 * @param objectMapper 用于解析完整供应商响应的 JSON 映射器
	 */
	public BailianAssistantContentExtractor(ObjectMapper objectMapper) {
		this.objectMapper =
				Objects.requireNonNull(objectMapper, "objectMapper must not be null");
	}

	/**
	 * 提取唯一、完整且由 assistant 返回的 content。
	 *
	 * <p>仅移除 content 首尾空白；完整包裹的 Markdown 代码围栏会原样保留，
	 * 交由后续评价 JSON 解析器处理。</p>
	 *
	 * @param rawResponse Provider 返回的百炼完整响应字符串
	 * @return 去除首尾空白后的 assistant content
	 */
	public String extract(String rawResponse) {
		if (rawResponse == null || rawResponse.isBlank()) {
			throw incompleteResponse();
		}

		JsonNode root = parseRoot(rawResponse);
		if (!root.isObject()) {
			throw invalidResponse();
		}
		if (hasProviderErrorCode(root)) {
			throw new EvaluationException(
					EvaluationErrorCode.PROVIDER_CALL_FAILED);
		}

		JsonNode output = requireNode(root, "output");
		if (!output.isObject()) {
			throw invalidResponse();
		}
		JsonNode choices = requireNode(output, "choices");
		if (!choices.isArray()) {
			throw invalidResponse();
		}
		if (choices.isEmpty()) {
			throw incompleteResponse();
		}
		if (choices.size() != 1) {
			throw invalidResponse();
		}

		JsonNode choice = choices.get(0);
		if (choice == null || !choice.isObject()) {
			throw invalidResponse();
		}
		String finishReason = requireText(choice, "finish_reason");
		if (!EXPECTED_FINISH_REASON.equals(finishReason)) {
			throw incompleteResponse();
		}

		JsonNode message = requireNode(choice, "message");
		if (!message.isObject()) {
			throw invalidResponse();
		}
		validateOptionalAssistantRole(message);

		return requireText(message, "content");
	}

	private JsonNode parseRoot(String rawResponse) {
		try {
			return objectMapper.readTree(rawResponse);
		}
		catch (JacksonException exception) {
			throw invalidResponse();
		}
	}

	private boolean hasProviderErrorCode(JsonNode root) {
		JsonNode code = root.get("code");
		if (code == null || code.isNull()) {
			return false;
		}
		return !code.isTextual() || !code.asString().isBlank();
	}

	private JsonNode requireNode(JsonNode parent, String fieldName) {
		JsonNode value = parent.get(fieldName);
		if (value == null || value.isNull()) {
			throw incompleteResponse();
		}
		return value;
	}

	private String requireText(JsonNode parent, String fieldName) {
		JsonNode value = requireNode(parent, fieldName);
		if (!value.isTextual()) {
			throw invalidResponse();
		}
		String text = value.asString().trim();
		if (text.isEmpty()) {
			throw incompleteResponse();
		}
		return text;
	}

	private void validateOptionalAssistantRole(JsonNode message) {
		JsonNode role = message.get("role");
		if (role == null || role.isNull()) {
			return;
		}
		if (!role.isTextual()
				|| !EXPECTED_MESSAGE_ROLE.equals(role.asString())) {
			throw invalidResponse();
		}
	}

	private EvaluationException invalidResponse() {
		return new EvaluationException(
				EvaluationErrorCode.PROVIDER_RESPONSE_INVALID);
	}

	private EvaluationException incompleteResponse() {
		return new EvaluationException(
				EvaluationErrorCode.PROVIDER_RESPONSE_INCOMPLETE);
	}
}
