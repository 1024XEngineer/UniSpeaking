package com.unispeaking.infrastructure.realtime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
}
