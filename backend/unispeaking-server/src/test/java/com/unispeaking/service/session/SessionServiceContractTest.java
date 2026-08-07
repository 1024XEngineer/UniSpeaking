package com.unispeaking.service.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.unispeaking.service.session.impl.CustomSessionServiceImpl;
import com.unispeaking.service.session.impl.FreeChatSessionServiceImpl;
import com.unispeaking.service.session.impl.IeltsSessionServiceImpl;
import com.unispeaking.component.session.SessionLifecycleManager;
import com.unispeaking.service.auth.AuthService;
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

		assertEquals(Set.of(
				"startSession",
				"endSession",
				"addMessage",
				"getSession",
				"getBySceneId"), methodNames);
	}

	@Test
	void isImplementedByEverySupportedScene() {
		assertTrue(SessionService.class.isAssignableFrom(
				FreeChatSessionServiceImpl.class));
		assertTrue(SessionService.class.isAssignableFrom(
				CustomSessionServiceImpl.class));
		assertTrue(SessionService.class.isAssignableFrom(
				IeltsSessionServiceImpl.class));
	}

	@Test
	void sessionLayerDoesNotOwnAuthentication() {
		for (Class<?> type : Set.of(
				FreeChatSessionServiceImpl.class,
				CustomSessionServiceImpl.class,
				IeltsSessionServiceImpl.class,
				SessionLifecycleManager.class)) {
			boolean dependsOnAuth = java.util.Arrays.stream(type.getDeclaredFields())
					.anyMatch(field -> AuthService.class.isAssignableFrom(field.getType()));
			assertTrue(!dependsOnAuth, type.getSimpleName() + " must not depend on AuthService");
		}
	}
}
