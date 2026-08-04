package com.unispeaking.component.session;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.unispeaking.domain.po.session.CustomSceneSession;
import org.junit.jupiter.api.Test;

class ActiveSessionRegistryTest {

	@Test
	void registersAtomicallyWithoutOverwritingAnExistingSession() {
		ActiveSessionRegistry registry = new ActiveSessionRegistry();
		CustomSceneSession original = new CustomSceneSession("session_1", "user_1");
		CustomSceneSession duplicate = new CustomSceneSession("session_1", "user_2");

		assertTrue(registry.registerIfAbsent(original));
		assertFalse(registry.registerIfAbsent(duplicate));
		assertSame(original, registry.findById("session_1").orElseThrow());
	}

	@Test
	void compensationRemovesOnlyTheSameSessionInstance() {
		ActiveSessionRegistry registry = new ActiveSessionRegistry();
		CustomSceneSession registered = new CustomSceneSession("session_1", "user_1");
		CustomSceneSession other = new CustomSceneSession("session_1", "user_1");
		registry.registerIfAbsent(registered);

		assertFalse(registry.remove("session_1", other));
		assertSame(registered, registry.findById("session_1").orElseThrow());
		assertTrue(registry.remove("session_1", registered));
		assertTrue(registry.findById("session_1").isEmpty());
	}
}
