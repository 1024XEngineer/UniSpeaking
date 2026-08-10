package com.unispeaking.service.scene;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.unispeaking.service.scene.impl.CustomSceneServiceImpl;
import com.unispeaking.service.scene.impl.FreeChatSceneServiceImpl;
import com.unispeaking.service.scene.impl.IeltsSceneServiceImpl;
import com.unispeaking.component.session.SessionLifecycleManager;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class SceneServiceContractTest {

	@Test
	void everySceneInterfaceExposesGenerateAndImplImplementsItsOwnInterface() {
		assertSceneGenerateShape(CustomSceneService.class, CustomSceneServiceImpl.class);
		assertSceneGenerateShape(FreeChatSceneService.class, FreeChatSceneServiceImpl.class);
		assertSceneGenerateShape(IeltsSceneService.class, IeltsSceneServiceImpl.class);
	}

	private void assertSceneGenerateShape(
			Class<?> sceneInterface,
			Class<?> implementation) {
		// 场景专用接口必须声明自己的 generate 主方法（不再继承公共基类）。
		assertTrue(Arrays.stream(sceneInterface.getDeclaredMethods())
				.anyMatch(method -> method.getName().equals("generate")),
				sceneInterface.getSimpleName() + " must declare generate");
		// 实现类必须实现对应的场景专用接口。
		assertTrue(sceneInterface.isAssignableFrom(implementation),
				implementation.getSimpleName() + " must implement " + sceneInterface.getSimpleName());
	}

	@Test
	void sceneImplementationsDoNotOwnSessionLifecycle() {
		for (Class<?> implementation : new Class<?>[] {
				CustomSceneServiceImpl.class,
				FreeChatSceneServiceImpl.class,
				IeltsSceneServiceImpl.class}) {
			assertFalse(Arrays.stream(implementation.getDeclaredMethods())
					.anyMatch(method -> method.getName().equals("startSession")),
					implementation.getSimpleName() + " must not own startSession");
			assertFalse(Arrays.stream(implementation.getDeclaredFields())
					.anyMatch(field -> SessionLifecycleManager.class.isAssignableFrom(
							field.getType())),
					implementation.getSimpleName() + " must not own the session lifecycle");
		}
	}
}
