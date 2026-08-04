package com.unispeaking.common.statemachine;

import com.unispeaking.domain.po.scene.ScenarioSuccessFactor;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class ScenarioSuccessFactorParser {

	private static final int DEFAULT_MINIMUM_USER_TURNS = 3;

	private final ObjectMapper objectMapper;

	public ScenarioSuccessFactorParser(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public ScenarioSuccessFactor parse(
			String successFactorJson,
			String fallbackGoal) {
		try {
			JsonNode root = objectMapper.readTree(successFactorJson);
			Map<String, String> outcomes = parseOutcomes(
					root.path("required_outcomes"));
			if (outcomes.isEmpty()) {
				outcomes.put("outcome_1", normalizedGoal(fallbackGoal));
			}
			return new ScenarioSuccessFactor(
					positiveOrDefault(
							root.path("minimum_user_turns").asInt(),
							DEFAULT_MINIMUM_USER_TURNS),
					positiveOrDefault(
							root.path("maximum_user_turns").asInt(),
							ScenarioSuccessFactor.HARD_MAXIMUM_USER_TURNS),
					outcomes,
					root.path("stop_when").asText(""),
					root.path("closing_instruction").asText(""));
		}
		catch (RuntimeException exception) {
			return fallback(fallbackGoal);
		}
	}

	private Map<String, String> parseOutcomes(JsonNode node) {
		Map<String, String> outcomes = new LinkedHashMap<>();
		if (!node.isArray()) {
			return outcomes;
		}
		int index = 1;
		for (JsonNode value : node) {
			String description = value.asText("").trim();
			if (!description.isBlank()) {
				outcomes.put("outcome_" + index++, description);
			}
		}
		return outcomes;
	}

	private ScenarioSuccessFactor fallback(String fallbackGoal) {
		return new ScenarioSuccessFactor(
				DEFAULT_MINIMUM_USER_TURNS,
				ScenarioSuccessFactor.HARD_MAXIMUM_USER_TURNS,
				Map.of("outcome_1", normalizedGoal(fallbackGoal)),
				"用户完成场景学习目标",
				"简短总结并自然结束对话");
	}

	private String normalizedGoal(String fallbackGoal) {
		return fallbackGoal == null || fallbackGoal.isBlank()
				? "完成当前对话目标"
				: fallbackGoal.trim();
	}

	private int positiveOrDefault(int value, int fallback) {
		return value > 0 ? value : fallback;
	}
}
