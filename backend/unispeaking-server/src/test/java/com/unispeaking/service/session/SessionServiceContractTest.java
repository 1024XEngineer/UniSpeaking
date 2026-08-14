package com.unispeaking.service.session;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.unispeaking.service.session.CustomSessionService;
import com.unispeaking.service.session.FreeChatSessionService;
import com.unispeaking.service.session.IeltsSessionService;
import com.unispeaking.component.session.SessionLifecycleManager;
import com.unispeaking.service.auth.AuthService;
import java.util.Arrays;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SessionServiceContractTest {

	@Test
	void everySessionServiceIsConcreteAndExposesLifecycleShape() {
		assertSessionShape(FreeChatSessionService.class);
		assertSessionShape(CustomSessionService.class);
		assertSessionShape(IeltsSessionService.class);
	}

	private void assertSessionShape(Class<?> service) {
		assertFalse(service.isInterface(),
				service.getSimpleName() + " must be a concrete class");
		Set<String> methodNames = Arrays.stream(service.getDeclaredMethods())
				.map(java.lang.reflect.Method::getName)
				.collect(java.util.stream.Collectors.toSet());
		// 接受 WS 实时帧的场景会话接口必须暴露 startSession/addMessage/endSession 生命周期形状。
		assertTrue(methodNames.contains("startSession"),
				service.getSimpleName() + " must declare startSession");
		assertTrue(methodNames.contains("addMessage"),
				service.getSimpleName() + " must declare addMessage (consumed by SessionMessageDispatcher)");
		assertTrue(methodNames.contains("endSession"),
				service.getSimpleName() + " must declare endSession");
	}

	@Test
	void sessionLayerDoesNotOwnAuthentication() {
		for (Class<?> type : Set.of(
				FreeChatSessionService.class,
				CustomSessionService.class,
				IeltsSessionService.class,
				SessionLifecycleManager.class)) {
			boolean dependsOnAuth = Arrays.stream(type.getDeclaredFields())
					.anyMatch(field -> AuthService.class.isAssignableFrom(field.getType()));
			assertTrue(!dependsOnAuth, type.getSimpleName() + " must not depend on AuthService");
		}
	}

	@Test
	void sessionLayerDoesNotOwnSceneStateMachines() {
		for (Class<?> type : Set.of(
				CustomSessionService.class,
				IeltsSessionService.class)) {
			boolean exposesStateTransition = Arrays.stream(
					type.getDeclaredMethods())
					.map(java.lang.reflect.Method::getName)
					.anyMatch(name -> name.contains("State"));
			assertTrue(!exposesStateTransition,
					type.getSimpleName() + " must not expose scene state transitions");
		}
		for (Class<?> type : Set.of(
				FreeChatSessionService.class,
				CustomSessionService.class,
				IeltsSessionService.class)) {
			boolean ownsStateMachine = Arrays.stream(type.getDeclaredFields())
					.map(field -> field.getType().getPackageName())
					.anyMatch(packageName -> packageName.endsWith(".statemachine"));
			assertTrue(!ownsStateMachine,
					type.getSimpleName() + " must not own scene state machines");
		}
	}
}
