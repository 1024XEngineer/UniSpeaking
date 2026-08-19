package com.unispeaking.service.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unispeaking.infrastructure.config.TurnProperties;
import com.unispeaking.infrastructure.realtime.TurnCredentialIssuer;
import com.unispeaking.service.auth.AuthService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RealtimeNetworkServiceTest {

	private AuthService authService;
	private TurnCredentialIssuer issuer;

	@BeforeEach
	void setUp() {
		authService = mock(AuthService.class);
		issuer = mock(TurnCredentialIssuer.class);
		when(authService.requireUserId(null)).thenReturn("user-123");
		when(issuer.issue()).thenReturn(new TurnCredentialIssuer.IssuedTurnCredential(
				"1893456000:opaque",
				"temporary-credential",
				Instant.ofEpochSecond(1893456000)));
	}

	@Test
	void keepsTurnDisabledAtZeroPercentRollout() {
		RealtimeNetworkService service = new RealtimeNetworkService(
				authService, properties(0), issuer);

		var response = service.getIceConfiguration(false);

		assertFalse(response.turnEnabled());
		assertEquals("all", response.iceTransportPolicy());
		assertTrue(response.iceServers().isEmpty());
		verify(issuer, never()).issue();
	}

	@Test
	void issuesCredentialsForFullRollout() {
		RealtimeNetworkService service = new RealtimeNetworkService(
				authService, properties(100), issuer);

		var response = service.getIceConfiguration(false);

		assertTrue(response.turnEnabled());
		assertEquals("all", response.iceTransportPolicy());
		assertEquals("temporary-credential", response.iceServers().getFirst().credential());
		assertEquals(Instant.ofEpochSecond(1893456000), response.expiresAt());
	}

	@Test
	void forceRelayBypassesRolloutForAuthenticatedTest() {
		RealtimeNetworkService service = new RealtimeNetworkService(
				authService, properties(0, Set.of("user-123")), issuer);

		var response = service.getIceConfiguration(true);

		assertTrue(response.turnEnabled());
		assertEquals("relay", response.iceTransportPolicy());
		verify(issuer).issue();
	}

	@Test
	void rejectsForceRelayForUsersOutsideTestAllowlist() {
		RealtimeNetworkService service = new RealtimeNetworkService(
				authService, properties(0), issuer);

		var response = service.getIceConfiguration(true);

		assertFalse(response.turnEnabled());
		verify(issuer, never()).issue();
	}

	private TurnProperties properties(int rolloutPercentage) {
		return properties(rolloutPercentage, Set.of());
	}

	private TurnProperties properties(int rolloutPercentage, Set<String> relayTestUserIds) {
		return new TurnProperties(
				true,
				List.of("turn:turn.example.cn:443?transport=udp"),
				"01234567890123456789012345678901",
				Duration.ofMinutes(5),
				rolloutPercentage,
				relayTestUserIds);
	}
}
