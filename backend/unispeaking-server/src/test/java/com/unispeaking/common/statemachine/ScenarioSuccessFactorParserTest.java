package com.unispeaking.common.statemachine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ScenarioSuccessFactorParserTest {

	private final ScenarioSuccessFactorParser parser =
			new ScenarioSuccessFactorParser(new ObjectMapper());

	@Test
	void capsExistingLongSceneAtTenEffectiveTurns() {
		var factor = parser.parse("""
				{
				  "minimum_user_turns": 5,
				  "maximum_user_turns": 18,
				  "required_outcomes": ["说明商品", "确认数量", "完成确认"],
				  "stop_when": "全部目标完成",
				  "closing_instruction": "自然结束"
				}
				""", "完成药店咨询");

		assertEquals(5, factor.minimumUserTurns());
		assertEquals(10, factor.maximumUserTurns());
		assertEquals(3, factor.requiredOutcomes().size());
		assertEquals("说明商品", factor.requiredOutcomes().get("outcome_1"));
	}

	@Test
	void malformedFactorFallsBackToLearningGoal() {
		var factor = parser.parse("not-json", "完成药店咨询");

		assertEquals(10, factor.maximumUserTurns());
		assertEquals("完成药店咨询", factor.requiredOutcomes().get("outcome_1"));
	}

	@Test
	void defaultsNonPositiveTurnsAndMissingOrBlankOutcomes() {
		var factor = parser.parse("""
				{"minimum_user_turns":0,"maximum_user_turns":-1,
				"required_outcomes":[" ",null," useful goal "]}
				""", "fallback");
		assertEquals(3, factor.minimumUserTurns());
		assertEquals(10, factor.maximumUserTurns());
		assertEquals("useful goal", factor.requiredOutcomes().get("outcome_1"));

		var missing = parser.parse("{\"required_outcomes\":{}}", " fallback goal ");
		assertEquals("fallback goal", missing.requiredOutcomes().get("outcome_1"));
	}

	@Test
	void malformedOrNullDocumentsUseDefaultGoalForNullAndBlankFallbacks() {
		assertEquals("完成当前对话目标",
				parser.parse("null", null).requiredOutcomes().get("outcome_1"));
		assertEquals("完成当前对话目标",
				parser.parse("bad-json", " ").requiredOutcomes().get("outcome_1"));
	}
}
