package com.unispeaking.common.evaluation.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.unispeaking.common.exception.evaluation.EvaluationException;
import com.unispeaking.domain.vo.scene.IeltsPart;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class IeltsTextAssessmentParserTest {

	private final IeltsTextAssessmentParser parser =
			new IeltsTextAssessmentParser(
					new EvaluationJsonDocumentParser(new ObjectMapper()));

	@Test
	void parsesPartDiagnosticAndFormatsPriorityImprovements() {
		var result = parser.parse(partJson(), IeltsPart.PART_1);

		assertEquals(new BigDecimal("7.0"), result.fluencyCoherenceBand());
		assertEquals(new BigDecimal("6.0"), result.lexicalResourceBand());
		assertEquals(3, result.strengths().size());
		assertEquals(
				"basic words；建议：more precise words。词汇可以更具体",
				result.improvements().getFirst());
	}

	@Test
	void acceptsFinalOnlyWithFinalAssessmentType() {
		var result = parser.parse(
				partJson()
						.replace("PART_1", "FULL_TEST")
						.replace("DIAGNOSTIC", "FINAL"),
				null);

		assertEquals(null, result.part());
		assertThrows(
				EvaluationException.class,
				() -> parser.parse(
						partJson().replace("PART_1", "FULL_TEST"),
						null));
	}

	@Test
	void normalizesInBetweenCriterionBandsDownward() {
		var result = parser.parse(
				partJson()
						.replace("\"band\":7", "\"band\":7.5")
						.replace("\"band\":6", "\"band\":\"6.5\""),
				IeltsPart.PART_1);

		assertEquals(new BigDecimal("7.0"), result.fluencyCoherenceBand());
		assertEquals(new BigDecimal("6.0"), result.lexicalResourceBand());
	}

	private String partJson() {
		return """
				{
				  "part":"PART_1",
				  "assessment_type":"DIAGNOSTIC",
				  "fluency_coherence":{"band":7,"strengths":["回答直接"],"issues":["偶有重复"],"evidence":["I usually read"]},
				  "lexical_resource":{"band":6,"strengths":["用词清楚"],"issues":["词汇较基础"],"evidence":["very good"]},
				  "grammatical_range_accuracy":{"band":6,"strengths":["简单句准确"],"issues":["复杂句有限"],"evidence":["I go there"]},
				  "pronunciation":{"band":null,"reason":"Pronunciation must be assessed from audio."},
				  "text_diagnostic_band":6.5,
				  "priority_improvements":[{"problem":"basic words","original_example":"very good","improved_example":"more precise words","explanation_zh":"词汇可以更具体"}],
				  "summary_zh":"表达基本连贯。",
				  "confidence":"HIGH"
				}
				""";
	}
}
