package com.unispeaking.service.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class EvaluationServiceContractTest {

	@Test
	void exposesOnlyTheFourEvaluationOperations() {
		Set<String> methods = Arrays.stream(
						EvaluationService.class.getDeclaredMethods())
				.map(method -> method.getName())
				.collect(Collectors.toSet());

		assertEquals(Set.of(
				"evaluateSentenceReading",
				"evaluateDialogueTurn",
				"generateDialogueReport",
				"getDialogueEvaluation"), methods);
		assertEquals(
				4,
				EvaluationService.class.getDeclaredMethods().length);
	}
}
