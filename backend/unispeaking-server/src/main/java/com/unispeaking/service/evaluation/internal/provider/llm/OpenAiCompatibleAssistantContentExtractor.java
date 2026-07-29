package com.unispeaking.service.evaluation.internal.provider.llm;

import com.unispeaking.service.evaluation.internal.exception.EvaluationErrorCode;
import com.unispeaking.service.evaluation.internal.exception.EvaluationException;
import java.util.Objects;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;

/**
 * 从 OpenAI-compatible 完整响应中严格提取唯一的 assistant content。
 *
 * <p>本类只处理供应商响应 envelope，不解析 content 中的评价 JSON。解析失败时
 * 统一使用评分模块的安全错误消息，避免原始模型响应进入异常或日志。</p>
 */
public final class OpenAiCompatibleAssistantContentExtractor {

	private static final String EXPECTED_FINISH_REASON = "stop";
	private static final String EXPECTED_MESSAGE_ROLE = "assistant";

	private final ObjectReader strictReader;

	/**
	 * 基于项目 ObjectMapper 创建独立的严格读取器，不改变全局 JSON 配置。
	 *
	 * @param objectMapper 项目提供的 Jackson 3 映射器
	 */
	public OpenAiCompatibleAssistantContentExtractor(ObjectMapper objectMapper) {
		ObjectMapper requiredMapper =
				Objects.requireNonNull(objectMapper, "objectMapper must not be null");
		this.strictReader = requiredMapper.reader()
				.with(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
				.with(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
				.with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
	}

	/**
	 * 提取唯一且完整结束的 assistant content。
	 *
	 * <p>该方法只接受根级 {@code choices} 的完整响应，不兼容裸 content，
	 * 从而保留对 {@code finish_reason} 的完整性检查。</p>
	 *
	 * @param rawResponse Provider 返回的完整 HTTP 响应 body
	 * @return 去除首尾空白后的 assistant content
	 * @throws EvaluationException 响应表示调用失败、结构无效或字段不完整
	 */
	public String extract(String rawResponse) {
		if (rawResponse == null || rawResponse.isBlank()) {
			throw incompleteResponse();
		}

		JsonNode root = parseRoot(rawResponse);
		if (hasProviderError(root)) {
			throw new EvaluationException(
					EvaluationErrorCode.PROVIDER_CALL_FAILED);
		}

		JsonNode choices = requireNode(root, "choices");
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
			JsonNode root = strictReader.readTree(rawResponse);
			if (root == null || !root.isObject()) {
				throw invalidResponse();
			}
			return root;
		} catch (JacksonException exception) {
			/*
			 * Jackson 异常可能包含输入片段，因此不保留原始 cause，也不把
			 * Provider 响应拼接到评分模块的异常消息中。
			 */
			throw invalidResponse();
		}
	}

	private boolean hasProviderError(JsonNode root) {
		JsonNode error = root.get("error");
		if (error != null && !error.isNull()) {
			return true;
		}

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
