package com.unispeaking.service.session;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.unispeaking.service.session.impl.CustomSessionServiceImpl;
import com.unispeaking.service.session.impl.FreeChatSessionServiceImpl;
import com.unispeaking.service.session.impl.IeltsSessionServiceImpl;
import com.unispeaking.component.session.SessionLifecycleManager;
import com.unispeaking.service.auth.AuthService;
import java.util.Arrays;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SessionServiceContractTest {

	@Test
	void everySessionInterfaceExposesLifecycleShapeAndImplImplementsOwnInterface() {
		assertSessionShape(FreeChatSessionService.class, FreeChatSessionServiceImpl.class);
		assertSessionShape(CustomSessionService.class, CustomSessionServiceImpl.class);
		assertSessionShape(IeltsSessionService.class, IeltsSessionServiceImpl.class);
	}

	private void assertSessionShape(
			Class<?> sessionInterface,
			Class<?> implementation) {
		Set<String> methodNames = Arrays.stream(sessionInterface.getDeclaredMethods())
				.map(java.lang.reflect.Method::getName)
				.collect(java.util.stream.Collectors.toSet());
		// 接受 WS 实时帧的场景会话接口必须暴露 startSession/addMessage/endSession 生命周期形状。
		assertTrue(methodNames.contains("startSession"),
				sessionInterface.getSimpleName() + " must declare startSession");
		assertTrue(methodNames.contains("addMessage"),
				sessionInterface.getSimpleName() + " must declare addMessage (consumed by SessionMessageDispatcher)");
		assertTrue(methodNames.contains("endSession"),
				sessionInterface.getSimpleName() + " must declare endSession");
		assertTrue(sessionInterface.isAssignableFrom(implementation),
				implementation.getSimpleName() + " must implement " + sessionInterface.getSimpleName());
	}

	@Test
	void sessionLayerDoesNotOwnAuthentication() {
		for (Class<?> type : Set.of(
				FreeChatSessionServiceImpl.class,
				CustomSessionServiceImpl.class,
				IeltsSessionServiceImpl.class,
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
				FreeChatSessionServiceImpl.class,
				CustomSessionServiceImpl.class,
				IeltsSessionServiceImpl.class)) {
			boolean ownsStateMachine = Arrays.stream(type.getDeclaredFields())
					.map(field -> field.getType().getPackageName())
					.anyMatch(packageName -> packageName.endsWith(".statemachine"));
			assertTrue(!ownsStateMachine,
					type.getSimpleName() + " must not own scene state machines");
		}
	}
}
