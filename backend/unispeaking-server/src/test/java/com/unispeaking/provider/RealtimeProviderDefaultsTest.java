package com.unispeaking.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.unispeaking.domain.dto.session.RealtimeConnectCommand;
import com.unispeaking.domain.vo.provider.AiCapability;
import com.unispeaking.domain.vo.provider.ProviderType;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.domain.vo.session.RealtimeConnectionResult;
import com.unispeaking.domain.vo.session.RealtimeCredential;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RealtimeProviderDefaultsTest {

	@Test
	void exposesDefaultSdpConnectionCredentialAndStopBehavior() {
		StubProvider provider = new StubProvider(ProviderType.QWEN);
		assertEquals(ProviderType.QWEN, provider.type());
		assertEquals(AiCapability.REALTIME, provider.capability());
		assertTrue(provider.requiresIssuedCredential());
		assertEquals("answer:null:offer:token", provider.exchangeRealtimeSdp("offer", "token"));

		Instant expiresAt = Instant.parse("2026-08-24T12:00:00Z");
		RealtimeConnectionResult result = provider.connect(
				new RealtimeConnectCommand("model", "offer", "user", "client", "scene", SceneType.FREE_CHAT, "voice"),
				new RealtimeCredential("token", expiresAt));
		assertEquals(ProviderType.QWEN, result.providerType());
		assertEquals("model", result.modelId());
		assertEquals("voice", result.voiceId());
		assertEquals("answer:model:offer:token", result.answerSdp());
		assertEquals(expiresAt, result.credentialExpiresAt());
		provider.stopSession("provider-session", "token", "finished");
	}

	@Test
	void rejectsARealtimeProviderWithoutAType() {
		assertThrows(IllegalArgumentException.class, () -> new StubProvider(null));
	}

	private static final class StubProvider extends RealtimeProvider {
		private StubProvider(ProviderType type) {
			super(type, Set.of("model"));
		}

		@Override
		public String exchangeRealtimeSdp(String modelId, String offerSdp, String token) {
			return "answer:" + modelId + ":" + offerSdp + ":" + token;
		}
	}
}
