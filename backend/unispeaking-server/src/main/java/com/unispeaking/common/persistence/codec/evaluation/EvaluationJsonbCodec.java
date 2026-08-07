package com.unispeaking.common.persistence.codec.evaluation;

import com.unispeaking.infrastructure.persistence.entity.evaluation.PronunciationDetailsJson;
import com.unispeaking.infrastructure.persistence.entity.evaluation.ReadingDetailsJson;
import com.unispeaking.common.exception.evaluation.EvaluationErrorCode;
import com.unispeaking.common.exception.evaluation.EvaluationException;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.ObjectWriter;

/**
 * 在评分持久化投影与 PostgreSQL JSONB 文本之间执行严格编解码。
 *
 * <p>读取时拒绝未知字段、重复字段、尾随内容及类型错误；任何失败只转换为
 * 安全的评分持久化错误，不保留可能包含用户文本的 Jackson 异常。</p>
 */
@Component
public final class EvaluationJsonbCodec {

	private static final Set<String> READING_ROOT_FIELDS = Set.of(
			"overall_score",
			"pronunciation_score",
			"fluency_score",
			"integrity_score",
			"rhythm_score",
			"ending_tone",
			"words");
	private static final Set<String> READING_WORD_FIELDS = Set.of(
			"index",
			"text",
			"read_status",
			"overall_score",
			"pronunciation_score",
			"is_prominent",
			"phonemes");
	private static final Set<String> PRONUNCIATION_ROOT_FIELDS =
			Set.of("words");
	private static final Set<String> PRONUNCIATION_WORD_FIELDS = Set.of(
			"index",
			"text",
			"pronunciation_score",
			"phonemes");
	private static final Set<String> PHONEME_FIELDS = Set.of(
			"index",
			"expected_phoneme",
			"actual_phoneme",
			"pronunciation_score",
			"start_position",
			"end_position");

	private final ObjectReader strictTreeReader;
	private final ObjectReader readingReader;
	private final ObjectReader pronunciationReader;
	private final ObjectWriter readingWriter;
	private final ObjectWriter pronunciationWriter;

	/**
	 * 创建不修改项目全局 ObjectMapper 配置的 JSONB 编解码器。
	 */
	public EvaluationJsonbCodec(ObjectMapper objectMapper) {
		ObjectMapper requiredMapper = Objects.requireNonNull(
				objectMapper,
				"objectMapper must not be null");
		this.strictTreeReader = requiredMapper.reader()
				.with(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
				.with(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
				.with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
		this.readingReader = requiredMapper.readerFor(ReadingDetailsJson.class);
		this.pronunciationReader =
				requiredMapper.readerFor(PronunciationDetailsJson.class);
		this.readingWriter = requiredMapper.writerFor(ReadingDetailsJson.class);
		this.pronunciationWriter =
				requiredMapper.writerFor(PronunciationDetailsJson.class);
	}

	/**
	 * 序列化完整跟读评分 JSONB。
	 */
	public String encodeReadingDetails(ReadingDetailsJson details) {
		if (details == null) {
			throw persistenceFailure();
		}
		try {
			return readingWriter.writeValueAsString(details);
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	/**
	 * 严格恢复完整跟读评分 JSONB。
	 */
	public ReadingDetailsJson decodeReadingDetails(String json) {
		JsonNode root = parseRoot(json);
		try {
			validateReadingDetails(root);
			return readingReader.readValue(root);
		}
		catch (EvaluationException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	/**
	 * 序列化单轮气泡的发音明细 JSONB，空 words 用于保存过短记录。
	 */
	public String encodePronunciationDetails(
			PronunciationDetailsJson details) {
		if (details == null) {
			throw persistenceFailure();
		}
		try {
			return pronunciationWriter.writeValueAsString(details);
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	/**
	 * 严格恢复单轮气泡的发音明细 JSONB。
	 */
	public PronunciationDetailsJson decodePronunciationDetails(String json) {
		JsonNode root = parseRoot(json);
		try {
			validatePronunciationDetails(root);
			return pronunciationReader.readValue(root);
		}
		catch (EvaluationException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	private JsonNode parseRoot(String json) {
		if (json == null || json.isBlank()) {
			throw persistenceFailure();
		}
		try {
			JsonNode root = strictTreeReader.readTree(json);
			if (root == null || !root.isObject()) {
				throw persistenceFailure();
			}
			return root;
		}
		catch (EvaluationException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	private void validateReadingDetails(JsonNode root) {
		requireExactObject(root, READING_ROOT_FIELDS);
		requireScore(root.get("overall_score"));
		requireScore(root.get("pronunciation_score"));
		requireScore(root.get("fluency_score"));
		requireScore(root.get("integrity_score"));
		requireScore(root.get("rhythm_score"));
		requireText(root.get("ending_tone"));

		JsonNode words = requireArray(root.get("words"), false);
		for (JsonNode word : words) {
			requireExactObject(word, READING_WORD_FIELDS);
			requireIndex(word.get("index"));
			requireText(word.get("text"));
			requireText(word.get("read_status"));
			requireScore(word.get("overall_score"));
			requireScore(word.get("pronunciation_score"));
			JsonNode prominent = word.get("is_prominent");
			if (prominent == null
					|| (!prominent.isNull() && !prominent.isBoolean())) {
				throw persistenceFailure();
			}
			validatePhonemes(word.get("phonemes"));
		}
	}

	private void validatePronunciationDetails(JsonNode root) {
		requireExactObject(root, PRONUNCIATION_ROOT_FIELDS);
		JsonNode words = requireArray(root.get("words"), true);
		for (JsonNode word : words) {
			requireExactObject(word, PRONUNCIATION_WORD_FIELDS);
			requireIndex(word.get("index"));
			requireText(word.get("text"));
			requireScore(word.get("pronunciation_score"));
			validatePhonemes(word.get("phonemes"));
		}
	}

	private void validatePhonemes(JsonNode value) {
		JsonNode phonemes = requireArray(value, false);
		for (JsonNode phoneme : phonemes) {
			requireExactObject(phoneme, PHONEME_FIELDS);
			requireIndex(phoneme.get("index"));
			requireText(phoneme.get("expected_phoneme"));
			requireText(phoneme.get("actual_phoneme"));
			requireScore(phoneme.get("pronunciation_score"));
			requirePositiveSpan(
					phoneme.get("start_position"),
					phoneme.get("end_position"));
		}
	}

	private void requirePositiveSpan(JsonNode start, JsonNode end) {
		requireIndex(start);
		requireIndex(end);
		if (end.intValue() <= start.intValue()) {
			throw persistenceFailure();
		}
	}

	private void requireExactObject(JsonNode value, Set<String> fields) {
		if (value == null
				|| !value.isObject()
				|| !Set.copyOf(value.propertyNames()).equals(fields)) {
			throw persistenceFailure();
		}
	}

	private JsonNode requireArray(JsonNode value, boolean allowEmpty) {
		if (value == null
				|| !value.isArray()
				|| (!allowEmpty && value.isEmpty())) {
			throw persistenceFailure();
		}
		return value;
	}

	private void requireIndex(JsonNode value) {
		if (value == null
				|| !value.isIntegralNumber()
				|| !value.canConvertToInt()
				|| value.intValue() < 0) {
			throw persistenceFailure();
		}
	}

	private void requireText(JsonNode value) {
		if (value == null
				|| !value.isString()
				|| value.asString().isBlank()) {
			throw persistenceFailure();
		}
	}

	private void requireScore(JsonNode value) {
		if (value == null || !value.isNumber()) {
			throw persistenceFailure();
		}
		BigDecimal score = value.decimalValue();
		if (score.compareTo(BigDecimal.ZERO) < 0
				|| score.compareTo(new BigDecimal("100")) > 0) {
			throw persistenceFailure();
		}
	}

	private EvaluationException persistenceFailure() {
		return new EvaluationException(EvaluationErrorCode.PERSISTENCE_FAILED);
	}
}
