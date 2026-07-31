package com.unispeaking.service.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.unispeaking.domain.po.scene.CustomSceneDefinition;
import com.unispeaking.service.scene.impl.ScenarioSuccessFactorParser;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ScenarioSuccessFactorParserTest {

	private final ScenarioSuccessFactorParser parser =
			new ScenarioSuccessFactorParser(new ObjectMapper());

	@Test
	void capsExistingLongSceneAtTenEffectiveTurns() {
		var factor = parser.parse(scene("""
				{
				  "minimum_user_turns": 5,
				  "maximum_user_turns": 18,
				  "required_outcomes": ["说明商品", "确认数量", "完成确认"],
				  "stop_when": "全部目标完成",
				  "closing_instruction": "自然结束"
				}
				"""));

		assertEquals(5, factor.minimumUserTurns());
		assertEquals(10, factor.maximumUserTurns());
		assertEquals(3, factor.requiredOutcomes().size());
		assertEquals("说明商品", factor.requiredOutcomes().get("outcome_1"));
	}

	@Test
	void malformedFactorFallsBackToLearningGoal() {
		var factor = parser.parse(scene("not-json"));

		assertEquals(10, factor.maximumUserTurns());
		assertEquals("完成药店咨询", factor.requiredOutcomes().get("outcome_1"));
	}

	private CustomSceneDefinition scene(String successFactor) {
		return new CustomSceneDefinition(
				"custom_1",
				"user_1",
				"药店咨询",
				"药店",
				"药剂师",
				"顾客",
				"完成药店咨询",
				"保持简洁",
				successFactor,
				List.of(),
				List.of(),
				List.of());
	}
}
