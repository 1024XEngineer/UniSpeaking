package com.unispeaking.service.scene;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.unispeaking.service.scene.CustomSceneService;
import com.unispeaking.service.scene.FreeChatSceneService;
import com.unispeaking.service.scene.IeltsSceneService;
import com.unispeaking.component.session.SessionLifecycleManager;
import com.unispeaking.domain.vo.scene.CustomStage;
import com.unispeaking.domain.vo.scene.IeltsStage;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class SceneServiceContractTest {

	@Test
	void everySceneServiceIsConcreteAndExposesGenerate() {
		assertSceneGenerateShape(CustomSceneService.class);
		assertSceneGenerateShape(FreeChatSceneService.class);
		assertSceneGenerateShape(IeltsSceneService.class);
	}

	private void assertSceneGenerateShape(Class<?> service) {
		assertFalse(service.isInterface(),
				service.getSimpleName() + " must be a concrete class");
		assertTrue(Arrays.stream(service.getDeclaredMethods())
				.anyMatch(method -> method.getName().equals("generate")),
				service.getSimpleName() + " must declare generate");
	}

	@Test
	void sceneImplementationsDoNotOwnSessionLifecycle() {
		for (Class<?> implementation : new Class<?>[] {
				CustomSceneService.class,
				FreeChatSceneService.class,
				IeltsSceneService.class}) {
			assertFalse(Arrays.stream(implementation.getDeclaredMethods())
					.anyMatch(method -> method.getName().equals("startSession")),
					implementation.getSimpleName() + " must not own startSession");
			assertFalse(Arrays.stream(implementation.getDeclaredFields())
					.anyMatch(field -> SessionLifecycleManager.class.isAssignableFrom(
							field.getType())),
					implementation.getSimpleName() + " must not own the session lifecycle");
		}
	}

	@Test
	void flowServicesExplicitlyOverrideEverySharedOperation() throws Exception {
		assertFlowOverrides(CustomSceneFlowService.class, CustomStage.class);
		assertFlowOverrides(IeltsSceneFlowService.class, IeltsStage.class);
		assertFalse(Arrays.stream(IeltsSceneFlowService.class.getMethods())
				.anyMatch(method -> method.getName().equals("next")
						&& method.getParameterCount() > 1),
				"IELTS flow must not expose stage rewind or resynchronization");
	}

	private void assertFlowOverrides(Class<?> service, Class<?> stageType)
			throws Exception {
		assertTrue(SceneFlowService.class.isAssignableFrom(service));
		assertOverride(service, "start", stageType, String.class);
		assertOverride(service, "current", stageType, String.class);
		assertOverride(service, "next", stageType, String.class);
		assertOverride(service, "isCompleted", boolean.class, String.class);
		assertOverride(service, "clear", void.class, String.class);
	}

	private void assertOverride(
			Class<?> service,
			String methodName,
			Class<?> returnType,
			Class<?>... parameterTypes) throws Exception {
		var method = service.getDeclaredMethod(methodName, parameterTypes);
		assertTrue(method.getReturnType().equals(returnType));
	}
}
