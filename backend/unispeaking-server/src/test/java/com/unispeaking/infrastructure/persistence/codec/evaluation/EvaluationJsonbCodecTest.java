package com.unispeaking.infrastructure.persistence.codec.evaluation;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.unispeaking.infrastructure.persistence.entity.evaluation.PronunciationDetailsJson;
import com.unispeaking.infrastructure.persistence.entity.evaluation.PronunciationDetailsJson.Phoneme;
import com.unispeaking.infrastructure.persistence.entity.evaluation.ReadingDetailsJson;
import com.unispeaking.common.exception.evaluation.EvaluationErrorCode;
import com.unispeaking.common.exception.evaluation.EvaluationException;
import com.unispeaking.common.evaluation.model.EndingTone;
import com.unispeaking.common.evaluation.model.WordReadStatus;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 验证两种 JSONB 投影的精确字段、完整音素和严格失败边界。
 */
class EvaluationJsonbCodecTest {

	private static final String VALID_READING_JSON = """
			{
			  "overall_score": 90,
			  "pronunciation_score": 89,
			  "fluency_score": 88,
			  "integrity_score": 87,
			  "rhythm_score": 86,
			  "ending_tone": "FALL",
			  "words": [{
			    "index": 0,
			    "text": "good",
			    "read_status": "NORMAL",
			    "overall_score": 85,
			    "pronunciation_score": 84,
			    "is_prominent": null,
			    "phonemes": [{
			      "index": 0,
			      "expected_phoneme": "g",
			      "actual_phoneme": "k",
			      "pronunciation_score": 83
			    }]
			  }]
			}
			""";

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final EvaluationJsonbCodec codec =
			new EvaluationJsonbCodec(objectMapper);

	@Test
	void roundTripsReadingDetailsWithExactSnakeCaseStructure()
			throws Exception {
		ReadingDetailsJson source = readingDetails();

		String json = codec.encodeReadingDetails(source);
		JsonNode root = objectMapper.readTree(json);
		JsonNode word = root.get("words").get(0);
		JsonNode phoneme = word.get("phonemes").get(0);

		assertAll(
				() -> assertEquals(
						Set.of(
								"overall_score",
								"pronunciation_score",
								"fluency_score",
								"integrity_score",
								"rhythm_score",
								"ending_tone",
								"words"),
						Set.copyOf(root.propertyNames())),
				() -> assertFalse(root.has("tone_score")),
				() -> assertEquals(
						Set.of(
								"index",
								"text",
								"read_status",
								"overall_score",
								"pronunciation_score",
								"is_prominent",
								"phonemes"),
						Set.copyOf(word.propertyNames())),
				() -> assertTrue(word.get("is_prominent").isNull()),
				() -> assertEquals(
						Set.of(
								"index",
								"expected_phoneme",
								"actual_phoneme",
								"pronunciation_score",
								"start_position",
								"end_position"),
						Set.copyOf(phoneme.propertyNames())),
				() -> assertFalse(phoneme.has("symbol")),
				() -> assertEquals(source, codec.decodeReadingDetails(json)));
	}

	@Test
	void roundTripsPronunciationDetailsWithoutReadingOnlyFields()
			throws Exception {
		PronunciationDetailsJson source = pronunciationDetails();

		String json = codec.encodePronunciationDetails(source);
		JsonNode root = objectMapper.readTree(json);
		JsonNode word = root.get("words").get(0);
		JsonNode phoneme = word.get("phonemes").get(0);

		assertAll(
				() -> assertEquals(
						Set.of("words"),
						Set.copyOf(root.propertyNames())),
				() -> assertEquals(
						Set.of(
								"index",
								"text",
								"pronunciation_score",
								"phonemes"),
						Set.copyOf(word.propertyNames())),
				() -> assertFalse(word.has("read_status")),
				() -> assertFalse(word.has("overall_score")),
				() -> assertFalse(word.has("is_prominent")),
				() -> assertFalse(root.has("ending_tone")),
				() -> assertEquals(
						"k",
						phoneme.get("actual_phoneme").asString()),
				() -> assertFalse(phoneme.has("symbol")),
				() -> assertEquals(
						source,
						codec.decodePronunciationDetails(json)));
	}

	@Test
	void supportsEmptyWordsOnlyForShortPronunciationDetails() {
		PronunciationDetailsJson shortDetails =
				new PronunciationDetailsJson(List.of());

		String json = codec.encodePronunciationDetails(shortDetails);

		assertAll(
				() -> assertEquals("{\"words\":[]}", json),
				() -> assertEquals(
						shortDetails,
						codec.decodePronunciationDetails(json)),
				() -> assertThrows(
						IllegalArgumentException.class,
						() -> new ReadingDetailsJson(
								score("0"),
								score("0"),
								score("0"),
								score("0"),
								score("0"),
								EndingTone.UNKNOWN,
								List.of())));
	}

	@Test
	void keepsSourceListsImmutableAndRejectsNullElements() {
		List<PronunciationDetailsJson.Word> mutableWords =
				new ArrayList<>(List.of(pronunciationWord()));
		PronunciationDetailsJson details =
				new PronunciationDetailsJson(mutableWords);

		mutableWords.clear();

		assertAll(
				() -> assertEquals(1, details.words().size()),
				() -> assertThrows(
						UnsupportedOperationException.class,
						() -> details.words().clear()),
				() -> assertThrows(
						NullPointerException.class,
						() -> new PronunciationDetailsJson(
								java.util.Arrays.asList(
										pronunciationWord(),
										null))));
	}

	@Test
	void rejectsUnknownDuplicateAndTrailingContent() {
		assertAll(
				() -> assertPersistenceFailure(
						VALID_READING_JSON.replace(
								"\"words\":",
								"\"unexpected\":1,\"words\":"),
						true),
				() -> assertPersistenceFailure(
						VALID_READING_JSON.replace(
								"\"actual_phoneme\": \"k\",",
								"\"symbol\":\"g\","
										+ "\"actual_phoneme\": \"k\","),
						true),
				() -> assertPersistenceFailure(
						VALID_READING_JSON.replace(
								"\"overall_score\": 90,",
								"\"overall_score\": 90,"
										+ "\"overall_score\": 91,"),
						true),
				() -> assertPersistenceFailure(
						VALID_READING_JSON + "{\"words\":[]}",
						true));
	}

	@Test
	void rejectsMissingWrongTypeOutOfRangeAndWrongRoot() {
		assertAll(
				() -> assertPersistenceFailure(
						VALID_READING_JSON.replace(
								"\"actual_phoneme\": \"k\",",
								""),
						true),
				() -> assertPersistenceFailure(
						VALID_READING_JSON.replace(
								"\"pronunciation_score\": 83",
								"\"pronunciation_score\": \"83\""),
						true),
				() -> assertPersistenceFailure(
						VALID_READING_JSON.replace(
								"\"pronunciation_score\": 83",
								"\"pronunciation_score\": 101"),
						true),
				() -> assertPersistenceFailure("[]", true),
				() -> assertPersistenceFailure(
						"{\"words\":{}}",
						false));
	}

	@Test
	void rejectsBlankNullAndIncompleteCodecInputsWithoutLeakingData() {
		assertAll(
				() -> assertPersistenceFailure(null, true),
				() -> assertPersistenceFailure(" \n\t ", false),
				() -> assertPersistenceFailure(
						"{\"secret_transcript\":\"do not expose me\"}",
						true),
				() -> assertCodecFailure(
						() -> codec.encodeReadingDetails(null)),
				() -> assertCodecFailure(
						() -> codec.encodePronunciationDetails(null)));
	}

	private void assertPersistenceFailure(
			String json,
			boolean reading) {
		assertCodecFailure(() -> {
			if (reading) {
				codec.decodeReadingDetails(json);
			}
			else {
				codec.decodePronunciationDetails(json);
			}
		});
	}

	private void assertCodecFailure(Runnable operation) {
		EvaluationException exception = assertThrows(
				EvaluationException.class,
				operation::run);

		assertAll(
				() -> assertEquals(
						EvaluationErrorCode.PERSISTENCE_FAILED,
						exception.errorCode()),
				() -> assertFalse(
						exception.getMessage().contains(
								"secret_transcript")),
				() -> assertFalse(
						exception.getMessage().contains(
								"do not expose me")),
				() -> assertNull(exception.getCause()));
	}

	private static ReadingDetailsJson readingDetails() {
		ReadingDetailsJson.Phoneme phoneme =
				new ReadingDetailsJson.Phoneme(
						0,
						"g",
						"k",
						score("83"));
		ReadingDetailsJson.Word word = new ReadingDetailsJson.Word(
				0,
				"good",
				WordReadStatus.NORMAL,
				score("85"),
				score("84"),
				null,
				List.of(phoneme));
		return new ReadingDetailsJson(
				score("90"),
				score("89"),
				score("88"),
				score("87"),
				score("86"),
				EndingTone.FALL,
				List.of(word));
	}

	private static PronunciationDetailsJson pronunciationDetails() {
		return new PronunciationDetailsJson(
				List.of(pronunciationWord()));
	}

	private static PronunciationDetailsJson.Word pronunciationWord() {
		return new PronunciationDetailsJson.Word(
				0,
				"cat",
				score("90"),
				List.of(new Phoneme(
						0,
						"k",
						"k",
						score("95"))));
	}

	private static BigDecimal score(String value) {
		return new BigDecimal(value);
	}
}
