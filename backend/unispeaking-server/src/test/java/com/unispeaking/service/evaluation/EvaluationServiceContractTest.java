package com.unispeaking.service.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.unispeaking.service.evaluation.CustomEvaluationService;
import com.unispeaking.service.evaluation.IeltsEvaluationService;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class EvaluationServiceContractTest {

	@Test
	void exposesOnlyTheDocumentedEvaluationOperations() {
		Set<String> methods = Arrays.stream(
						EvaluationService.class.getDeclaredMethods())
				.filter(method -> !method.isSynthetic())
				.map(method -> method.getName())
				.collect(Collectors.toSet());

		assertEquals(Set.of(
				"evaluateTurn",
				"generateReport",
				"getEvaluation"), methods);
		assertFalse(EvaluationService.class.isInterface());
		assertTrue(EvaluationService.class.isAssignableFrom(
				CustomEvaluationService.class));
		assertTrue(EvaluationService.class.isAssignableFrom(
				IeltsEvaluationService.class));
		assertTrue(Arrays.stream(CustomEvaluationService.class.getDeclaredMethods())
				.noneMatch(method -> method.getName().equals("generateDialogueReport")));
	}

	@Test
	void concreteEvaluationServicesExplicitlyOverrideSharedOperations()
			throws Exception {
		assertEvaluationOverrides(CustomEvaluationService.class);
		assertEvaluationOverrides(IeltsEvaluationService.class);
	}

	private void assertEvaluationOverrides(Class<?> service) throws Exception {
		assertTrue(EvaluationService.class.isAssignableFrom(service));
		assertOverride(service, "evaluateTurn",
				com.unispeaking.domain.dto.evaluation
						.DialogueTurnEvaluationCommand.class);
		assertOverride(service, "generateReport", String.class);
		assertOverride(service, "getEvaluation", String.class);
	}

	private void assertOverride(
			Class<?> service,
			String methodName,
			Class<?>... parameterTypes) throws Exception {
		service.getDeclaredMethod(methodName, parameterTypes);
	}
}
