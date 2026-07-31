package com.unispeaking.common.evaluation.parser;

import com.unispeaking.common.exception.evaluation.EvaluationErrorCode;
import com.unispeaking.common.exception.evaluation.EvaluationException;
import java.util.Objects;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;

/**
 * 将 LLM assistant content 解析为唯一、严格的评价 JSON 对象。
 *
 * <p>解析器不从说明文字中搜索 JSON，只允许纯 JSON 对象或一层完整的
 * lowercase {@code json} Markdown 围栏。重复字段和尾随内容在树节点覆盖前
 * 即被拒绝，防止模型输出的歧义内容进入评分。</p>
 */
public final class EvaluationJsonDocumentParser {

	private static final String JSON_FENCE_PREFIX_LF = "```json\n";
	private static final String JSON_FENCE_PREFIX_CRLF = "```json\r\n";
	private static final String FENCE_SUFFIX_LF = "\n```";
	private static final String FENCE_SUFFIX_CRLF = "\r\n```";
	private static final String FENCE_MARKER = "```";

	private final ObjectReader strictReader;

	/**
	 * 基于共享 ObjectMapper 创建独立的严格读取器，不改变项目全局 JSON 配置。
	 *
	 * @param objectMapper 项目提供的 Jackson 3 映射器
	 */
	public EvaluationJsonDocumentParser(ObjectMapper objectMapper) {
		ObjectMapper requiredMapper =
				Objects.requireNonNull(objectMapper, "objectMapper must not be null");
		this.strictReader = requiredMapper.reader()
				.with(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
				.with(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
				.with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
	}

	/**
	 * 解析一份完整的评价 JSON 对象。
	 *
	 * @param assistantContent 从供应商响应中提取的 assistant content
	 * @return 严格解析后的 JSON object 根节点
	 */
	public JsonNode parseObject(String assistantContent) {
		String document = unwrapDocument(assistantContent);
		try {
			JsonNode root = strictReader.readTree(document);
			if (root == null || !root.isObject()) {
				throw invalidResponse();
			}
			return root;
		}
		catch (JacksonException exception) {
			// Jackson 异常可能携带输入片段，因此不作为 cause 或消息继续传播。
			throw invalidResponse();
		}
	}

	private String unwrapDocument(String assistantContent) {
		if (assistantContent == null || assistantContent.isBlank()) {
			throw incompleteResponse();
		}
		String trimmed = assistantContent.trim();
		if (!trimmed.startsWith(FENCE_MARKER)) {
			return trimmed;
		}

		int contentStart;
		if (trimmed.startsWith(JSON_FENCE_PREFIX_CRLF)) {
			contentStart = JSON_FENCE_PREFIX_CRLF.length();
		} else if (trimmed.startsWith(JSON_FENCE_PREFIX_LF)) {
			contentStart = JSON_FENCE_PREFIX_LF.length();
		} else {
			throw invalidResponse();
		}

		int contentEnd;
		if (trimmed.endsWith(FENCE_SUFFIX_CRLF)) {
			contentEnd = trimmed.length() - FENCE_SUFFIX_CRLF.length();
		} else if (trimmed.endsWith(FENCE_SUFFIX_LF)) {
			contentEnd = trimmed.length() - FENCE_SUFFIX_LF.length();
		} else {
			throw invalidResponse();
		}
		if (contentEnd < contentStart) {
			throw invalidResponse();
		}

		String fencedDocument = trimmed.substring(contentStart, contentEnd).trim();
		if (fencedDocument.isEmpty()) {
			throw incompleteResponse();
		}
		if (fencedDocument.contains(FENCE_MARKER)) {
			throw invalidResponse();
		}
		return fencedDocument;
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
