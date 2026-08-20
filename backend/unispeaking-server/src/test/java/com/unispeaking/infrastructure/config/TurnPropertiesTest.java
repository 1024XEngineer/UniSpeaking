package com.unispeaking.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TurnPropertiesTest {

	@Test
	void acceptsDisabledConfigurationWithoutCredentials() {
		TurnProperties properties = new TurnProperties(false, List.of(), "", null, 0, Set.of());

		assertDoesNotThrow(properties::validate);
		assertEquals(List.of(), properties.urls());
		assertFalse(properties.canForceRelay("user-1"));
	}

	@Test
	void acceptsEnabledUdpTurnConfiguration() {
		TurnProperties properties = enabledProperties(25);

		assertDoesNotThrow(properties::validate);
		assertEquals(25, properties.rolloutPercentage());
	}

	@Test
	void rejectsEnabledConfigurationWithoutCredentialTtl() {
		TurnProperties properties = new TurnProperties(
				true,
				List.of("turn:turn.example.cn:443?transport=udp"),
				"01234567890123456789012345678901",
				null,
				10,
				Set.of());

		assertThrows(IllegalStateException.class, properties::validate);
	}

	@Test
	void rejectsUnsafeOrIncompleteEnabledConfiguration() {
		assertThrows(IllegalStateException.class, () -> new TurnProperties(
				true,
				List.of("turn:turn.example.cn:443?transport=tcp"),
				"short",
				Duration.ofMinutes(5),
				101,
				Set.of()).validate());
		assertThrows(IllegalStateException.class, () -> new TurnProperties(
				true,
				List.of("turns:turn.example.cn:443?transport=udp"),
				"01234567890123456789012345678901",
				Duration.ofMinutes(5),
				10,
				Set.of()).validate());
	}

	static TurnProperties enabledProperties(int rolloutPercentage) {
		return new TurnProperties(
				true,
				List.of("turn:turn.example.cn:443?transport=udp"),
				"01234567890123456789012345678901",
				Duration.ofMinutes(5),
				rolloutPercentage,
				Set.of("user-1"));
	}
}
