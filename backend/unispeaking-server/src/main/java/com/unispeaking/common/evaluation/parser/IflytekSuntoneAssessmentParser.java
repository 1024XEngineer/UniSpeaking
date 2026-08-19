package com.unispeaking.common.evaluation.parser;

import com.unispeaking.common.exception.evaluation.EvaluationErrorCode;
import com.unispeaking.common.exception.evaluation.EvaluationException;
import com.unispeaking.common.evaluation.model.EndingTone;
import com.unispeaking.common.evaluation.model.PronunciationAssessmentResult;
import com.unispeaking.common.evaluation.model.PronunciationPhonemeResult;
import com.unispeaking.common.evaluation.model.PronunciationWordResult;
import com.unispeaking.common.evaluation.model.WordReadStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 解码 Suntone 最终响应并映射为评分模块的发音评测模型。
 */
public final class IflytekSuntoneAssessmentParser {

	private static final BigDecimal HUNDRED =
			new BigDecimal("100");
	private static final int SCORE_SCALE = 2;

	private final ObjectMapper objectMapper;

	public IflytekSuntoneAssessmentParser(
			ObjectMapper objectMapper) {
		this.objectMapper = Objects.requireNonNull(
				objectMapper,
				"objectMapper must not be null");
	}

	public PronunciationAssessmentResult parse(
			String rawResponse) {
		JsonNode envelope = parseObject(rawResponse);
		JsonNode header = requiredObject(envelope, "header");
		int code = requiredInteger(header, "code");
		if (code != 0) {
			throw new EvaluationException(
					EvaluationErrorCode.PROVIDER_CALL_FAILED);
		}
		if (requiredInteger(header, "status") != 2) {
			throw incomplete();
		}

		JsonNode payload = requiredObject(envelope, "payload");
		JsonNode encodedResult =
				requiredObject(payload, "result");
		if (requiredInteger(encodedResult, "status") != 2) {
			throw incomplete();
		}
		JsonNode text = encodedResult.get("text");
		if (text == null || text.isNull()) {
			throw incomplete();
		}
		if (!text.isTextual() || text.asString().isBlank()) {
			throw invalid();
		}

		byte[] decoded;
		try {
			decoded = Base64.getDecoder().decode(
					text.asString());
		}
		catch (IllegalArgumentException exception) {
			throw invalid();
		}
		JsonNode document = parseObject(
				new String(decoded, StandardCharsets.UTF_8));
		JsonNode result = requiredObject(document, "result");

		List<PronunciationWordResult> words =
				parseWords(result.get("words"));
		if (words.isEmpty()) {
			throw incomplete();
		}

		return new PronunciationAssessmentResult(
				requiredScore(result, "overall"),
				requiredScore(result, "rhythm"),
				optionalScore(result, "tone"),
				requiredScore(result, "integrity"),
				requiredScore(result, "pronunciation"),
				requiredScore(result, "fluency"),
				parseEndingTone(result.get("rear_tone")),
				List.copyOf(words));
	}

	private List<PronunciationWordResult> parseWords(
			JsonNode rawWords) {
		if (rawWords == null || !rawWords.isArray()) {
			throw incomplete();
		}
		List<PronunciationWordResult> words =
				new ArrayList<>();
		for (JsonNode rawWord : rawWords) {
			if (rawWord == null || !rawWord.isObject()) {
				throw invalid();
			}
			JsonNode charType = rawWord.get("charType");
			if (charType != null
					&& charType.isIntegralNumber()
					&& charType.intValue() == 1) {
				continue;
			}
			JsonNode scores =
					requiredObject(rawWord, "scores");
			List<PronunciationPhonemeResult> phonemes =
					parsePhonemes(rawWord.get("phonemes"));
			if (phonemes.isEmpty()) {
				throw incomplete();
			}
			words.add(new PronunciationWordResult(
					words.size(),
					requiredText(rawWord, "word"),
					parseReadStatus(rawWord.get("readType")),
					requiredScore(scores, "overall"),
					requiredScore(scores, "pronunciation"),
					optionalProminence(scores.get("prominence")),
					List.copyOf(phonemes)));
		}
		return words;
	}

	private List<PronunciationPhonemeResult> parsePhonemes(
			JsonNode rawPhonemes) {
		if (rawPhonemes == null || !rawPhonemes.isArray()) {
			throw incomplete();
		}
		List<PronunciationPhonemeResult> phonemes =
				new ArrayList<>();
		for (JsonNode rawPhoneme : rawPhonemes) {
			if (rawPhoneme == null
					|| !rawPhoneme.isObject()) {
				throw invalid();
			}
			String phoneme =
					requiredText(rawPhoneme, "phoneme");
			JsonNode rawPhone = rawPhoneme.get("phone");
			String phone = rawPhone != null
							&& rawPhone.isTextual()
							&& !rawPhone.asString().isBlank()
					? rawPhone.asString().trim()
					: phoneme;
			JsonNode span =
					requiredObject(rawPhoneme, "span");
			int start = requiredInteger(span, "start");
			int end = requiredInteger(span, "end");
			boolean unmatched = start == -1 && end == -1;
			if (!unmatched
					&& (start < 0 || end < 0 || end <= start)) {
				throw invalid();
			}
			phonemes.add(new PronunciationPhonemeResult(
					phonemes.size(),
					phone,
					phoneme,
					requiredScore(
							rawPhoneme,
							"pronunciation"),
					start,
					end));
		}
		return phonemes;
	}

	private WordReadStatus parseReadStatus(JsonNode value) {
		if (value == null || value.isNull()) {
			return WordReadStatus.NORMAL;
		}
		if (!value.isIntegralNumber()
				|| !value.canConvertToInt()) {
			throw invalid();
		}
		return switch (value.intValue()) {
			case 0 -> WordReadStatus.NORMAL;
			case 1 -> WordReadStatus.INSERTION_BEFORE;
			default -> WordReadStatus.OMITTED;
		};
	}

	private Boolean optionalProminence(JsonNode value) {
		if (value == null || value.isNull()) {
			return null;
		}
		if (!value.isIntegralNumber()
				|| (value.intValue() != 0
						&& value.intValue() != 1)) {
			throw invalid();
		}
		return value.intValue() == 1;
	}

	private EndingTone parseEndingTone(JsonNode value) {
		if (value == null || value.isNull()) {
			return EndingTone.UNKNOWN;
		}
		if (!value.isTextual()) {
			throw invalid();
		}
		return switch (value.asString()
				.trim()
				.toLowerCase(Locale.ROOT)) {
			case "rise" -> EndingTone.RISE;
			case "fall" -> EndingTone.FALL;
			case "level" -> EndingTone.LEVEL;
			case "", "unknown" -> EndingTone.UNKNOWN;
			default -> EndingTone.UNKNOWN;
		};
	}

	private BigDecimal requiredScore(
			JsonNode parent,
			String field) {
		BigDecimal score = optionalScore(parent, field);
		if (score == null) {
			throw incomplete();
		}
		return score;
	}

	private BigDecimal optionalScore(
			JsonNode parent,
			String field) {
		JsonNode value = parent.get(field);
		if (value == null || value.isNull()) {
			return null;
		}
		if (!value.isNumber()) {
			throw invalid();
		}
		BigDecimal score = value.decimalValue();
		if (score.compareTo(BigDecimal.ZERO) < 0
				|| score.compareTo(HUNDRED) > 0) {
			throw invalid();
		}
		return score.setScale(
				SCORE_SCALE,
				RoundingMode.HALF_UP);
	}

	private JsonNode parseObject(String value) {
		if (value == null || value.isBlank()) {
			throw invalid();
		}
		try {
			JsonNode parsed = objectMapper.readTree(value);
			if (parsed == null || !parsed.isObject()) {
				throw invalid();
			}
			return parsed;
		}
		catch (JacksonException exception) {
			throw invalid();
		}
	}

	private JsonNode requiredObject(
			JsonNode parent,
			String field) {
		JsonNode value = parent.get(field);
		if (value == null || value.isNull()) {
			throw incomplete();
		}
		if (!value.isObject()) {
			throw invalid();
		}
		return value;
	}

	private String requiredText(
			JsonNode parent,
			String field) {
		JsonNode value = parent.get(field);
		if (value == null || value.isNull()) {
			throw incomplete();
		}
		if (!value.isTextual()
				|| value.asString().isBlank()) {
			throw invalid();
		}
		return value.asString().trim();
	}

	private int requiredInteger(
			JsonNode parent,
			String field) {
		JsonNode value = parent.get(field);
		if (value == null || value.isNull()) {
			throw incomplete();
		}
		if (!value.isIntegralNumber()
				|| !value.canConvertToInt()) {
			throw invalid();
		}
		return value.intValue();
	}

	private EvaluationException invalid() {
		return new EvaluationException(
				EvaluationErrorCode.PROVIDER_RESPONSE_INVALID);
	}

	private EvaluationException incomplete() {
		return new EvaluationException(
				EvaluationErrorCode.PROVIDER_RESPONSE_INCOMPLETE);
	}
}
