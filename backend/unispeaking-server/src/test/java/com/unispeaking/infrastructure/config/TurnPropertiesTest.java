package com.unispeaking.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

	@Test
	void normalizesNullableAndBlankCollections() {
		TurnProperties nullable = new TurnProperties(false, null, null, null, 0, null);
		TurnProperties filtered = new TurnProperties(
				false, List.of(" ", " turn:host:443?transport=udp "), " secret ", null, 0,
				Set.of(" ", " user-1 "));

		assertEquals(List.of(), nullable.urls());
		assertEquals(Set.of(), nullable.relayTestUserIds());
		assertEquals("", nullable.sharedSecret());
		assertEquals(List.of("turn:host:443?transport=udp"), filtered.urls());
		assertEquals(Set.of("user-1"), filtered.relayTestUserIds());
		assertEquals("secret", filtered.sharedSecret());
		assertTrue(filtered.canForceRelay(" user-1 "));
		assertFalse(filtered.canForceRelay(null));
	}

	@Test
	void rejectsBothRolloutBoundsEvenWhenDisabled() {
		assertThrows(IllegalStateException.class,
				() -> new TurnProperties(false, null, null, null, -1, null).validate());
		assertThrows(IllegalStateException.class,
				() -> new TurnProperties(false, null, null, null, 101, null).validate());
	}

	@Test
	void rejectsEveryEnabledConfigurationBoundary() {
		String secret = "01234567890123456789012345678901";
		assertThrows(IllegalStateException.class, () -> new TurnProperties(
				true, List.of("turn:host:443?transport=udp"), secret,
				Duration.ofSeconds(59), 0, Set.of()).validate());
		assertThrows(IllegalStateException.class, () -> new TurnProperties(
				true, List.of("turn:host:443?transport=udp"), secret,
				Duration.ofMinutes(61), 0, Set.of()).validate());
		assertThrows(IllegalStateException.class, () -> new TurnProperties(
				true, List.of(), secret, Duration.ofMinutes(1), 0, Set.of()).validate());
		assertThrows(IllegalStateException.class, () -> new TurnProperties(
				true, List.of("turns:host:443?transport=udp"), secret,
				Duration.ofHours(1), 0, Set.of()).validate());
		assertThrows(IllegalStateException.class, () -> new TurnProperties(
				true, List.of("turn:host:443?transport=tcp"), secret,
				Duration.ofHours(1), 0, Set.of()).validate());
		assertThrows(IllegalStateException.class, () -> new TurnProperties(
				true, List.of("turn:host:443?transport=udp"), "short",
				Duration.ofHours(1), 0, Set.of()).validate());
		assertDoesNotThrow(() -> new TurnProperties(
				true, List.of("TURN:host:443?TRANSPORT=UDP"), secret,
				Duration.ofHours(1), 100, Set.of()).validate());
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
