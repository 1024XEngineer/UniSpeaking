package com.unispeaking.common.evaluation.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
	void rejectsInsufficientAssessmentAsNoFormalScore() {
		assertThrows(
				EvaluationException.class,
				() -> parser.parse(validJson().replace("\"ok\"", "\"insufficient_data\"")));
	}

	@Test
	void rejectsEnglishOnlyReasons() {
		assertThrows(
				EvaluationException.class,
				() -> parser.parse(validJson().replace(
						"时态正确",
						"The tense is correct")));
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
