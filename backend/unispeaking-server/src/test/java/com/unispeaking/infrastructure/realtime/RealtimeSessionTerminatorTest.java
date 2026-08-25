package com.unispeaking.infrastructure.realtime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

import java.time.Instant;

import com.unispeaking.domain.po.session.FreeChatSceneSession;
import com.unispeaking.domain.vo.provider.ProviderType;
import com.unispeaking.provider.AiProviderRegistry;
import com.unispeaking.provider.RealtimeProvider;
import org.junit.jupiter.api.Test;

class RealtimeSessionTerminatorTest {

	@Test
	void stopsTheProviderSessionSelectedDuringConnection() {
		AiProviderRegistry registry = mock(AiProviderRegistry.class);
		RealtimeProvider provider = mock(RealtimeProvider.class);
		when(registry.getRealtimeProvider("qwen3.5-omni-plus-realtime"))
				.thenReturn(provider);
		FreeChatSceneSession session = new FreeChatSceneSession("local-1", "user-1");
		session.setProviderType(ProviderType.QINIU);
		session.setModel("qwen3.5-omni-plus-realtime");
		session.bindProviderSession("rti-session-1");
		session.setProviderTraceId("official-request-01");

		new RealtimeSessionTerminator(registry).stopBestEffort(session, "client_completed");

		verify(provider).stopSession("rti-session-1", null, "client_completed");
		verify(registry).recordRealtimeSession(
				org.mockito.ArgumentMatchers.eq("user-1"),
				org.mockito.ArgumentMatchers.eq("local-1"),
				org.mockito.ArgumentMatchers.eq("qwen3.5-omni-plus-realtime"),
				org.mockito.ArgumentMatchers.eq("official-request-01"),
				org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any());
	}

	@Test
	void recordsDirectSdpSessionsWithoutAnExternalProviderSession() {
		AiProviderRegistry registry = mock(AiProviderRegistry.class);
		FreeChatSceneSession session = new FreeChatSceneSession("local-1", "user-1");
		session.setModel("qwen3.5-omni-flash-realtime");
		session.setProviderTraceId("official-request-02");

		new RealtimeSessionTerminator(registry).stopBestEffort(session, "client_completed");

		verify(registry).recordRealtimeSession(
				org.mockito.ArgumentMatchers.eq("user-1"),
				org.mockito.ArgumentMatchers.eq("local-1"),
				org.mockito.ArgumentMatchers.eq("qwen3.5-omni-flash-realtime"),
				org.mockito.ArgumentMatchers.eq("official-request-02"),
				org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any());
	}

	@Test
	void ignoresSessionsWithoutAnExternalProviderSession() {
		AiProviderRegistry registry = mock(AiProviderRegistry.class);
		FreeChatSceneSession session = new FreeChatSceneSession("local-1", "user-1");

		new RealtimeSessionTerminator(registry).stopBestEffort(session, "client_completed");

		verifyNoInteractions(registry);
	}

	@Test
	void coversNullBlankFailureAndRecordedEndTimeBranches() {
		AiProviderRegistry registry = mock(AiProviderRegistry.class);
		RealtimeSessionTerminator terminator = new RealtimeSessionTerminator(registry);
		terminator.stopBestEffort(null, "reason");
		FreeChatSceneSession blank = new FreeChatSceneSession("blank", "user");
		blank.setModel(" ");
		terminator.stopBestEffort(blank, "reason");
		verifyNoInteractions(registry);

		RealtimeProvider provider = mock(RealtimeProvider.class);
		when(registry.getRealtimeProvider("model")).thenReturn(provider);
		doThrow(new IllegalStateException("stop failed")).when(provider).stopSession("provider", null, "reason");
		FreeChatSceneSession failedStop = new FreeChatSceneSession("local", "user");
		failedStop.setModel("model");
		failedStop.bindProviderSession("provider");
		failedStop.complete(Instant.parse("2026-08-24T12:00:00Z"));
		terminator.stopBestEffort(failedStop, "reason");
		verify(registry).recordRealtimeSession(
				org.mockito.ArgumentMatchers.eq("user"), org.mockito.ArgumentMatchers.eq("local"),
				org.mockito.ArgumentMatchers.eq("model"), org.mockito.ArgumentMatchers.isNull(),
				org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(Instant.parse("2026-08-24T12:00:00Z")));

		doThrow(new IllegalStateException("record failed")).when(registry).recordRealtimeSession(
				org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("record-failure"),
				org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
		FreeChatSceneSession recordFailure = new FreeChatSceneSession("record-failure", "user");
		recordFailure.setModel("model");
		recordFailure.bindProviderSession(" ");
		terminator.stopBestEffort(recordFailure, "reason");
	}
}
