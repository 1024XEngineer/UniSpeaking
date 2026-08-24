package com.unispeaking.service.session;

import com.unispeaking.domain.dto.session.IceServerConfigurationResponse;
import com.unispeaking.infrastructure.config.TurnProperties;
import com.unispeaking.infrastructure.realtime.TurnCredentialIssuer;
import com.unispeaking.service.auth.AuthService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RealtimeNetworkService {

	private final AuthService authService;
	private final TurnProperties properties;
	private final TurnCredentialIssuer credentialIssuer;

	public RealtimeNetworkService(
			AuthService authService,
			TurnProperties properties,
			TurnCredentialIssuer credentialIssuer) {
		this.authService = authService;
		this.properties = properties;
		this.credentialIssuer = credentialIssuer;
	}

	public IceServerConfigurationResponse getIceConfiguration(boolean forceRelay) {
		String userId = authService.requireUserId(null);
		boolean relayTest = forceRelay && properties.canForceRelay(userId);
		if (!properties.enabled() || (forceRelay && !relayTest)
				|| (!forceRelay && !isIncludedInRollout(userId))) {
			return new IceServerConfigurationResponse(false, "all", List.of(), null);
		}
		TurnCredentialIssuer.IssuedTurnCredential credential = credentialIssuer.issue();
		IceServerConfigurationResponse.IceServer iceServer =
				new IceServerConfigurationResponse.IceServer(
						properties.urls(), credential.username(), credential.credential());
		return new IceServerConfigurationResponse(
				true,
				relayTest ? "relay" : "all",
				List.of(iceServer),
				credential.expiresAt());
	}

	private boolean isIncludedInRollout(String userId) {
		if (properties.rolloutPercentage() == 100) return true;
		if (properties.rolloutPercentage() == 0) return false;
		return Math.floorMod(userId.hashCode(), 100) < properties.rolloutPercentage();
	}
}
