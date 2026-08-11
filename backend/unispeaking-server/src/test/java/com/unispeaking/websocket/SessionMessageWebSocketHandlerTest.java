package com.unispeaking.websocket;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unispeaking.component.session.SessionMessageDispatcher;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

class SessionMessageWebSocketHandlerTest {
	@Test
	void acceptsProviderSessionBindingForTheAuthenticatedSession() throws Exception {
		SessionMessageDispatcher dispatcher = mock(SessionMessageDispatcher.class);
		SessionMessageWebSocketHandler handler = new SessionMessageWebSocketHandler(
				new ObjectMapper(), dispatcher);
		WebSocketSession socket = mock(WebSocketSession.class);
		when(socket.getAttributes()).thenReturn(new HashMap<>(java.util.Map.of(
				SessionWebSocketAuthenticationInterceptor.AUTHENTICATED_USER_ID, "user-1")));
		when(socket.isOpen()).thenReturn(true);

		handler.handleMessage(socket, new TextMessage("""
				{"type":"bind","sessionId":"session-1","providerSessionId":"sess_qwen_1"}
				"""));

		verify(dispatcher).bindProviderSession("user-1", "session-1", "sess_qwen_1");
		var ack = org.mockito.ArgumentCaptor.forClass(TextMessage.class);
		verify(socket).sendMessage(ack.capture());
		assertTrue(ack.getValue().getPayload().contains("session.bind.accepted"));
	}

	@Test
	void rejectsProviderSessionBindingWithoutAnId() throws Exception {
		SessionMessageDispatcher dispatcher = mock(SessionMessageDispatcher.class);
		SessionMessageWebSocketHandler handler = new SessionMessageWebSocketHandler(
				new ObjectMapper(), dispatcher);
		WebSocketSession socket = mock(WebSocketSession.class);
		when(socket.getAttributes()).thenReturn(new HashMap<>(java.util.Map.of(
				SessionWebSocketAuthenticationInterceptor.AUTHENTICATED_USER_ID, "user-1")));
		when(socket.isOpen()).thenReturn(true);

		handler.handleMessage(socket, new TextMessage(
				"{\"type\":\"bind\",\"sessionId\":\"session-1\"}"));

		verify(dispatcher, org.mockito.Mockito.never())
				.bindProviderSession(any(), any(), any());
		var ack = org.mockito.ArgumentCaptor.forClass(TextMessage.class);
		verify(socket).sendMessage(ack.capture());
		assertTrue(ack.getValue().getPayload().contains("session.bind.failed"));
	}
}
