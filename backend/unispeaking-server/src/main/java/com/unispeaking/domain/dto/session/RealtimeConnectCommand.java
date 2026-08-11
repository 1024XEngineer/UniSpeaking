package com.unispeaking.domain.dto.session;

/**
 * Provider-neutral input for establishing a realtime WebRTC session.
 */
public record RealtimeConnectCommand(
		String localSessionId,
		String userId,
		String modelId,
		String voiceId,
		String offerSdp) {

	public RealtimeConnectCommand withModel(String resolvedModelId) {
		return new RealtimeConnectCommand(
				localSessionId,
				userId,
				resolvedModelId,
				voiceId,
				offerSdp);
	}
}
