package com.unispeaking.service.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class EvaluationServiceContractTest {

	@Test
	void exposesOnlyTheDocumentedEvaluationOperations() {
		Set<String> methods = Arrays.stream(
						EvaluationService.class.getDeclaredMethods())
				.map(method -> method.getName())
				.collect(Collectors.toSet());

		assertEquals(Set.of(
				"evaluateTurn",
				"generateReport",
				"getEvaluation"), methods);
		assertEquals(3, EvaluationService.class.getDeclaredMethods().length);
	}
}
