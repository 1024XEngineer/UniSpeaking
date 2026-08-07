package com.unispeaking.service.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.unispeaking.service.scene.impl.CustomSceneServiceImpl;
import com.unispeaking.service.scene.impl.FreeChatSceneServiceImpl;
import com.unispeaking.service.scene.impl.IeltsSceneServiceImpl;
import com.unispeaking.service.session.SessionService;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class SceneServiceContractTest {

	@Test
	void baseContractContainsOnlyGenerateAndAllSceneServicesImplementIt() {
		assertEquals(1, SceneService.class.getDeclaredMethods().length);
		assertEquals(
				"generate",
				SceneService.class.getDeclaredMethods()[0].getName());
		assertTrue(SceneService.class.isAssignableFrom(CustomSceneServiceImpl.class));
		assertTrue(SceneService.class.isAssignableFrom(FreeChatSceneServiceImpl.class));
		assertTrue(SceneService.class.isAssignableFrom(IeltsSceneServiceImpl.class));
	}

	@Test
	void sceneImplementationsDoNotOwnSessionLifecycle() {
		for (Class<?> implementation : new Class<?>[] {
				CustomSceneServiceImpl.class,
				FreeChatSceneServiceImpl.class,
				IeltsSceneServiceImpl.class}) {
			assertFalse(Arrays.stream(implementation.getDeclaredMethods())
					.anyMatch(method -> method.getName().equals("startSession")));
			assertFalse(Arrays.stream(implementation.getDeclaredFields())
					.anyMatch(field -> SessionService.class.isAssignableFrom(
							field.getType())));
		}
	}
}
