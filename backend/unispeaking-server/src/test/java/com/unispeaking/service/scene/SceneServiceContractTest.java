package com.unispeaking.service.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SceneServiceContractTest {

	@Test
	void baseContractContainsOnlyGenerateAndBothSceneServicesExtendIt() {
		assertEquals(1, SceneService.class.getDeclaredMethods().length);
		assertEquals(
				"generate",
				SceneService.class.getDeclaredMethods()[0].getName());
		assertTrue(SceneService.class.isAssignableFrom(CustomSceneService.class));
		assertTrue(SceneService.class.isAssignableFrom(IELTSSceneService.class));
	}
}
