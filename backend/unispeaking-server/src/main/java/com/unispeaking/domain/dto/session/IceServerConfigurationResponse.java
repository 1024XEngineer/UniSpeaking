package com.unispeaking.domain.dto.session;

import java.time.Instant;
import java.util.List;

public record IceServerConfigurationResponse(
		boolean turnEnabled,
		String iceTransportPolicy,
		List<IceServer> iceServers,
		Instant expiresAt) {

	public record IceServer(List<String> urls, String username, String credential) {}
}
