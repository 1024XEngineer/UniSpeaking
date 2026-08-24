package com.unispeaking.common.evaluation.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.unispeaking.common.exception.evaluation.EvaluationErrorCode;
import com.unispeaking.common.exception.evaluation.EvaluationException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ConversationLanguageAssessmentParserTest {

	private final ConversationLanguageAssessmentParser parser =
			new ConversationLanguageAssessmentParser(
					new EvaluationJsonDocumentParser(new ObjectMapper()));

	@Test
	void parsesDocumentRubricAndCollectsEvidence() {
		var result = parser.parse(validJson());

		assertEquals(new BigDecimal("76"), result.grammarScore());
		assertEquals(new BigDecimal("72"), result.vocabularyScore());
		assertEquals(new BigDecimal("74"), result.textNaturalnessScore());
		assertEquals(3, result.strengths().size());
		assertEquals(3, result.improvements().size());
	}

	@Test
	void rejectsNullBlankMalformedAndNonObjectDocuments() {
		assertError(null, EvaluationErrorCode.PROVIDER_RESPONSE_INCOMPLETE);
		assertError(" \t\n ", EvaluationErrorCode.PROVIDER_RESPONSE_INCOMPLETE);
		assertError("not json", EvaluationErrorCode.PROVIDER_RESPONSE_INVALID);
		assertError("[]", EvaluationErrorCode.PROVIDER_RESPONSE_INVALID);
		assertError("```JSON\n{}\n```", EvaluationErrorCode.PROVIDER_RESPONSE_INVALID);
		assertError("```json\n{}", EvaluationErrorCode.PROVIDER_RESPONSE_INVALID);
		assertError("```json\n\n```", EvaluationErrorCode.PROVIDER_RESPONSE_INCOMPLETE);
	}

	@Test
	void rejectsStatusAndRootFieldViolations() {
		assertError(validJson().replace(
				"\"data_quality_notes\":[]",
				"\"extra\":1,\"data_quality_notes\":[]"),
				EvaluationErrorCode.PROVIDER_RESPONSE_INVALID);
		assertError(validJson().replace(
				"\"assessment_status\":\"ok\"",
				"\"assessment_status\":\"pending\""),
				EvaluationErrorCode.PROVIDER_RESPONSE_INVALID);
		assertError(validJson().replace(
				"\"assessment_status\":\"ok\"",
				"\"assessment_status\":\"insufficient_data\""),
				EvaluationErrorCode.PROVIDER_RESPONSE_INCOMPLETE);
		assertError(validJson().replace(
				"\"assessment_status\":\"ok\"",
				"\"assessment_status\":null"),
				EvaluationErrorCode.PROVIDER_RESPONSE_INCOMPLETE);
		assertError(validJson().replace(
				"\"assessment_status\":\"ok\"",
				"\"assessment_status\":123"),
				EvaluationErrorCode.PROVIDER_RESPONSE_INVALID);
		assertError(validJson().replace(
				"\"data_quality_notes\":[]",
				"\"data_quality_notes\":null"),
				EvaluationErrorCode.PROVIDER_RESPONSE_INCOMPLETE);
	}

	@Test
	void rejectsScoreRangePrecisionAndConfidenceViolations() {
		assertError(validJson().replace("\"grammar\":76", "\"grammar\":-0.01"),
				EvaluationErrorCode.PROVIDER_RESPONSE_INVALID);
		assertError(validJson().replace("\"grammar\":76", "\"grammar\":100.01"),
				EvaluationErrorCode.PROVIDER_RESPONSE_INVALID);
		assertError(validJson().replace("\"grammar\":76", "\"grammar\":76.123"),
				EvaluationErrorCode.PROVIDER_RESPONSE_INVALID);
		assertError(validJson().replace("\"grammar\":76", "\"grammar\":\"76\""),
				EvaluationErrorCode.PROVIDER_RESPONSE_INVALID);
		assertError(validJson().replace("\"grammar\":76", "\"grammar\":null"),
				EvaluationErrorCode.PROVIDER_RESPONSE_INCOMPLETE);
		assertError(validJson().replace("\"confidence\":0.8", "\"confidence\":-0.1"),
				EvaluationErrorCode.PROVIDER_RESPONSE_INVALID);
		assertError(validJson().replace("\"confidence\":0.8", "\"confidence\":1.01"),
				EvaluationErrorCode.PROVIDER_RESPONSE_INVALID);
		assertError(validJson().replace("\"confidence\":0.8", "\"confidence\":\"0.8\""),
				EvaluationErrorCode.PROVIDER_RESPONSE_INCOMPLETE);
		assertError(validJson().replace("\"confidence\":0.8", "\"confidence\":null"),
				EvaluationErrorCode.PROVIDER_RESPONSE_INCOMPLETE);
	}

	@Test
	void rejectsDimensionAndFeedbackShapeViolations() {
		assertError(validJson().replace(
				"\"dimensions\":{",
				"\"dimensions\":[]"),
				EvaluationErrorCode.PROVIDER_RESPONSE_INVALID);
		assertError(validJson().replace(
				"\"grammar\":{",
				"\"grammar\":{\"extra\":1,"),
				EvaluationErrorCode.PROVIDER_RESPONSE_INVALID);
		assertError(validJson().replace(
				"\"strengths\":[{\"evidence\":\"I went\",\"reason\":\"时态正确\"}]",
				"\"strengths\":null"),
				EvaluationErrorCode.PROVIDER_RESPONSE_INCOMPLETE);
		assertError(validJson().replace(
				"\"strengths\":[{\"evidence\":\"I went\",\"reason\":\"时态正确\"}]",
				"\"strengths\":[1]"),
				EvaluationErrorCode.PROVIDER_RESPONSE_INVALID);
		assertError(validJson().replace(
				"\"improvements\":[{\"evidence\":\"go yesterday\",\"correction\":\"went yesterday\",\"reason\":\"使用过去式\"}]",
				"\"improvements\":[{\"evidence\":\"go yesterday\",\"reason\":\"使用过去式\"}]"),
				EvaluationErrorCode.PROVIDER_RESPONSE_INCOMPLETE);
		assertError(validJson().replace(
				"\"improvements\":[{\"evidence\":\"go yesterday\",\"correction\":\"went yesterday\",\"reason\":\"使用过去式\"}]",
				"\"improvements\":[{\"evidence\":\"go yesterday\",\"correction\":\"   \",\"reason\":\"使用过去式\"}]"),
				EvaluationErrorCode.PROVIDER_RESPONSE_INCOMPLETE);
	}

	@Test
	void rejectsFeedbackLanguageLimitsAndUnknownFields() {
		assertError(validJson().replace(
				"\"reason\":\"时态正确\"",
				"\"reason\":\"The tense is correct\""),
				EvaluationErrorCode.PROVIDER_RESPONSE_INVALID);
		assertEquals("I went：时态正确", parser.parse(validJson().replace(
				"\"reason\":\"时态正确\"",
				"\"reason\":\"时态正确\",\"reazon\":\"额外信息\""))
				.strengths().getFirst());
		assertEquals("go yesterday → went yesterday：使用过去式", parser.parse(validJson().replace(
				"\"correction\":\"went yesterday\"",
				"\"correction\":\"went yesterday\",\"suggestion\":\"went\""))
				.improvements().getFirst());
		String tooManyStrengths = validJson().replace(
				"\"strengths\":[{\"evidence\":\"I went\",\"reason\":\"时态正确\"}]",
				"\"strengths\":[{\"evidence\":\"I went\",\"reason\":\"时态正确\"},{\"evidence\":\"I ran\",\"reason\":\"表达清楚\"},{\"evidence\":\"I ate\",\"reason\":\"内容完整\"},{\"evidence\":\"I slept\",\"reason\":\"信息准确\"}]");
		assertError(tooManyStrengths, EvaluationErrorCode.PROVIDER_RESPONSE_INVALID);
	}

	@Test
	void acceptsBoundaryScoresAndUsesPreferredCorrectionField() {
		String document = validJson()
				.replace("\"grammar\":76", "\"grammar\":0.00")
				.replace("\"vocabulary\":72", "\"vocabulary\":100.00")
				.replace("\"text_naturalness\":74", "\"text_naturalness\":74.25")
				.replace("\"evidence\":\"I went\"", "\"evidence\":\"  I went  \"");

		var result = parser.parse(document);

		assertEquals(0, result.grammarScore().compareTo(new BigDecimal("0.00")));
		assertEquals(0, result.vocabularyScore().compareTo(new BigDecimal("100.00")));
		assertEquals(0, result.textNaturalnessScore().compareTo(new BigDecimal("74.25")));
		assertTrue(result.strengths().getFirst().startsWith("I went："));
		assertTrue(result.improvements().getFirst().contains("went yesterday"));
	}

	@Test
	void rejectsDuplicateKeysAndTrailingTokens() {
		assertError(validJson().replace(
				"\"assessment_status\":\"ok\"",
				"\"assessment_status\":\"ok\",\"assessment_status\":\"ok\""),
				EvaluationErrorCode.PROVIDER_RESPONSE_INVALID);
		assertError(validJson() + " trailing", EvaluationErrorCode.PROVIDER_RESPONSE_INVALID);
	}

	private void assertError(String document, EvaluationErrorCode expected) {
		EvaluationException exception = assertThrows(
				EvaluationException.class,
				() -> parser.parse(document));
		assertEquals(expected, exception.errorCode());
	}

	private String validJson() {
		return """
				{
				  "assessment_status":"ok",
				  "scores":{"grammar":76,"vocabulary":72,"text_naturalness":74},
				  "confidence":0.8,
				  "dimensions":{
				    "grammar":{
				      "strengths":[{"evidence":"I went","reason":"时态正确"}],
				      "improvements":[{"evidence":"go yesterday","correction":"went yesterday","reason":"使用过去式"}]
				    },
				    "vocabulary":{
				      "strengths":[{"evidence":"went hiking","reason":"用词准确"}],
				      "improvements":[{"evidence":"very good","suggestion":"enjoyable","reason":"表达更具体"}]
				    },
				    "text_naturalness":{
				      "strengths":[{"evidence":"That sounds fun","reason":"衔接自然"}],
				      "improvements":[{"evidence":"near the city mountain","suggestion":"a mountain near the city","reason":"语序更自然"}]
				    }
				  },
				  "data_quality_notes":[]
				}
				""";
	}
}
