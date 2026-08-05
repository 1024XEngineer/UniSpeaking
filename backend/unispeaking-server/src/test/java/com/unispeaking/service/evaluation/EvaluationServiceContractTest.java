package com.unispeaking.service.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class EvaluationServiceContractTest {

	@Test
	void exposesOnlyTheFiveEvaluationOperations() {
		Set<String> methods = Arrays.stream(
						EvaluationService.class.getDeclaredMethods())
				.map(method -> method.getName())
				.collect(Collectors.toSet());

		assertEquals(Set.of(
				"evaluateSpeech",
				"evaluateSentenceReading",
				"evaluateDialogueTurn",
				"generateDialogueReport",
				"getDialogueEvaluation"), methods);
		assertEquals(
				5,
				EvaluationService.class.getDeclaredMethods().length);
	}
}
