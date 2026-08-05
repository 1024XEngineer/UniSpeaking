package com.unispeaking.service.session;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class SessionServiceContractTest {

	@Test
	void exposesOnlyGenericSessionLifecycleMethods() {
		Set<String> methodNames = java.util.Arrays.stream(
				SessionService.class.getDeclaredMethods())
				.map(java.lang.reflect.Method::getName)
				.collect(Collectors.toSet());

		assertEquals(Set.of("startSession", "endSession", "addMessage"), methodNames);
	}
}
