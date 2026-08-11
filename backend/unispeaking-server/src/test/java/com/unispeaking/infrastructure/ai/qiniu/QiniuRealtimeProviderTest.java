package com.unispeaking.infrastructure.ai.qiniu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.domain.dto.session.RealtimeConnectCommand;
import com.unispeaking.domain.vo.provider.ProviderType;
import com.unispeaking.domain.vo.session.RealtimeConnectionResult;
import com.unispeaking.domain.vo.session.RealtimeTransportType;
import com.unispeaking.infrastructure.config.QiniuRealtimeProperties;
import com.unispeaking.provider.AiProviderRegistry;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QiniuRealtimeProviderTest {

	private static final Instant EXPIRES_AT = Instant.parse("2026-08-11T05:00:00Z");

	@Test
	void registersTheQiniuRealtimeModel() {
		QiniuRealtimeProvider provider = provider(mock(QiniuRtiClient.class));

		assertEquals(ProviderType.QINIU, provider.type());
		assertTrue(provider.supports(AiProviderRegistry.QINIU_REALTIME_PLUS));
	}

	@Test
	void createsControlSessionThenExchangesOfferSdp() {
		QiniuRtiClient client = mock(QiniuRtiClient.class);
		QiniuRealtimeProvider provider = provider(client);
		RealtimeConnectCommand command = command();
		QiniuRtiClient.CreatedSession created = new QiniuRtiClient.CreatedSession(
				"rti-session-1",
				"trace-1",
				URI.create("https://rtc.example.test/session-1"),
				"rtc-token",
				EXPIRES_AT);
		when(client.createSession(command, "voice-katerina")).thenReturn(created);
		when(client.exchangeSdp(created, "offer-sdp")).thenReturn("answer-sdp");

		RealtimeConnectionResult result = provider.connect(command);

		assertEquals("rti-session-1", result.providerSessionId());
		assertEquals("answer-sdp", result.answerSdp());
		assertEquals(EXPIRES_AT, result.credentialExpiresAt());
		assertEquals(ProviderType.QINIU, result.providerType());
		assertEquals(AiProviderRegistry.QINIU_REALTIME_PLUS, result.modelId());
		assertEquals(RealtimeTransportType.PLATFORM_RTC, result.transportType());
		verify(client).createSession(command, "voice-katerina");
		verify(client).exchangeSdp(created, "offer-sdp");
	}

	@Test
	void stopsTheProviderSessionWhenTheClientEnds() {
		QiniuRtiClient client = mock(QiniuRtiClient.class);
		QiniuRealtimeProvider provider = provider(client);

		provider.stop("rti-session-1");

		verify(client).stopSession("rti-session-1", "client_completed");
	}

	@Test
	void stopsCreatedSessionWhenRtcSignalingFails() {
		QiniuRtiClient client = mock(QiniuRtiClient.class);
		QiniuRealtimeProvider provider = provider(client);
		RealtimeConnectCommand command = command();
		QiniuRtiClient.CreatedSession created = new QiniuRtiClient.CreatedSession(
				"rti-session-1",
				"trace-1",
				URI.create("https://rtc.example.test/session-1"),
				"rtc-token",
				EXPIRES_AT);
		when(client.createSession(command, "voice-katerina")).thenReturn(created);
		when(client.exchangeSdp(created, "offer-sdp"))
				.thenThrow(new BusinessException("QINIU_RTI_RESPONSE_INVALID", "missing answer"));

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> provider.connect(command));

		assertEquals("QINIU_RTI_RESPONSE_INVALID", exception.code());
		verify(client).stopSession("rti-session-1", "signaling_failed");
	}

	@Test
	void rejectsUnsupportedVoiceBeforeCreatingSession() {
		QiniuRtiClient client = mock(QiniuRtiClient.class);
		QiniuRealtimeProvider provider = provider(client);
		RealtimeConnectCommand command = new RealtimeConnectCommand(
				"local-session-1",
				"user-1",
				AiProviderRegistry.QINIU_REALTIME_PLUS,
				"unknown-voice",
				"offer-sdp");

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> provider.connect(command));

		assertEquals("QINIU_VOICE_PROFILE_NOT_CONFIGURED", exception.code());
		verify(client, never()).createSession(command, "voice-katerina");
	}

	@Test
	void keepsTheLegacySdpMethodUnavailableWithoutSessionContext() {
		QiniuRealtimeProvider provider = provider(mock(QiniuRtiClient.class));

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> provider.exchangeRealtimeSdp(
						AiProviderRegistry.QINIU_REALTIME_PLUS,
						"offer-sdp",
						"token"));

		assertEquals("QINIU_SESSION_CONTEXT_REQUIRED", exception.code());
	}

	private QiniuRealtimeProvider provider(QiniuRtiClient client) {
		return new QiniuRealtimeProvider(client, properties());
	}

	private QiniuRealtimeProperties properties() {
		return new QiniuRealtimeProperties(
				"https://rti.example.test",
				"qiniu-api-key",
				"app-1",
				"qwen-profile",
				"coach-profile",
				"language-learning",
				Map.of("katerina", "voice-katerina"),
				Duration.ofSeconds(1),
				Duration.ofSeconds(1),
				1_048_576);
	}

	private RealtimeConnectCommand command() {
		return new RealtimeConnectCommand(
				"local-session-1",
				"user-1",
				AiProviderRegistry.QINIU_REALTIME_PLUS,
				"Katerina",
				"offer-sdp");
	}
}
