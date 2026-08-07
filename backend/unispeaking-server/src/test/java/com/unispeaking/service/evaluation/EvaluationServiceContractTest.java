package com.unispeaking.service.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.unispeaking.service.evaluation.impl.CustomEvaluationServiceImpl;
import com.unispeaking.service.evaluation.impl.IeltsEvaluationServiceImpl;
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
		assertTrue(EvaluationService.class.isAssignableFrom(
				CustomEvaluationService.class));
		assertTrue(EvaluationService.class.isAssignableFrom(
				IeltsEvaluationService.class));
		assertTrue(CustomEvaluationService.class.isAssignableFrom(
				CustomEvaluationServiceImpl.class));
		assertTrue(IeltsEvaluationService.class.isAssignableFrom(
				IeltsEvaluationServiceImpl.class));
		assertTrue(Arrays.stream(CustomEvaluationService.class.getDeclaredMethods())
				.noneMatch(method -> method.getName().equals("generateDialogueReport")));
	}
}
