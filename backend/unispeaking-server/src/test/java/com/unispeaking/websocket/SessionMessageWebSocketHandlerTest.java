package com.unispeaking.websocket;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.component.session.SessionMessageDispatcher;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

class SessionMessageWebSocketHandlerTest {

	private WebSocketSession authenticatedSocket(String userId) {
		WebSocketSession socket = mock(WebSocketSession.class);
		when(socket.getAttributes()).thenReturn(new HashMap<>(userId == null
				? java.util.Map.of()
				: java.util.Map.of(SessionWebSocketAuthenticationInterceptor.AUTHENTICATED_USER_ID, userId)));
		when(socket.isOpen()).thenReturn(true);
		return socket;
	}

	private String ack(WebSocketSession socket) throws Exception {
		var ack = org.mockito.ArgumentCaptor.forClass(TextMessage.class);
		verify(socket).sendMessage(ack.capture());
		return ack.getValue().getPayload();
	}
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
	void activatesQuotaForTheAuthenticatedSession() throws Exception {
		SessionMessageDispatcher dispatcher = mock(SessionMessageDispatcher.class);
		SessionMessageWebSocketHandler handler = new SessionMessageWebSocketHandler(
				new ObjectMapper(), dispatcher);
		WebSocketSession socket = authenticatedSocket("user-1");

		handler.handleMessage(socket, new TextMessage(
				"{\"type\":\"activate\",\"sessionId\":\"session-1\"}"));

		verify(dispatcher).activateSession("user-1", "session-1");
		assertTrue(ack(socket).contains("session.activate.accepted"));
	}

	@Test
	void preservesTheQuotaErrorCodeWhenActivationIsRejected() throws Exception {
		SessionMessageDispatcher dispatcher = mock(SessionMessageDispatcher.class);
		when(dispatcher.activateSession("user-1", "session-1"))
				.thenThrow(new BusinessException("USER_QUOTA_EXHAUSTED", "今日练习额度已用完"));
		SessionMessageWebSocketHandler handler = new SessionMessageWebSocketHandler(
				new ObjectMapper(), dispatcher);
		WebSocketSession socket = authenticatedSocket("user-1");

		handler.handleMessage(socket, new TextMessage(
				"{\"type\":\"activate\",\"sessionId\":\"session-1\"}"));

		assertTrue(ack(socket).contains("USER_QUOTA_EXHAUSTED"));
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

	@Test
	void dispatchesMessageAndEndAliasesAndDefaultsMissingTypeToMessage() throws Exception {
		SessionMessageDispatcher dispatcher = mock(SessionMessageDispatcher.class);
		SessionMessageWebSocketHandler handler = new SessionMessageWebSocketHandler(new ObjectMapper(), dispatcher);
		WebSocketSession socket = authenticatedSocket("user-1");

		handler.handleMessage(socket, new TextMessage(
				"{\"type\":\"session.message\",\"sessionId\":\" s1 \",\"message\":{\"owner\":1,\"content\":\"hi\"}}"));
		verify(dispatcher).addMessage(eq("user-1"), eq("s1"), any());
		assertTrue(ack(socket).contains("session.message.accepted"));

		org.mockito.Mockito.clearInvocations(socket);
		handler.handleMessage(socket, new TextMessage(
				"{\"type\":\"endSession\",\"sessionId\":\"s1\",\"stopTime\":\"now\"}"));
		verify(dispatcher).endSession("user-1", "s1", "now");
		assertTrue(ack(socket).contains("session.end.accepted"));

		org.mockito.Mockito.clearInvocations(socket);
		handler.handleMessage(socket, new TextMessage(
				"{\"sessionId\":\"s1\",\"message\":{\"owner\":0,\"content\":\"hello\"}}"));
		verify(dispatcher, org.mockito.Mockito.times(2)).addMessage(eq("user-1"), eq("s1"), any());
		assertTrue(ack(socket).contains("session.message.accepted"));
	}

	@Test
	void rejectsUnsupportedTypesMissingSessionOrAuthenticationAndKeepsClosedSocketQuiet() throws Exception {
		SessionMessageDispatcher dispatcher = mock(SessionMessageDispatcher.class);
		SessionMessageWebSocketHandler handler = new SessionMessageWebSocketHandler(new ObjectMapper(), dispatcher);
		WebSocketSession socket = authenticatedSocket("user-1");

		handler.handleMessage(socket, new TextMessage("{\"type\":\"unknown\",\"sessionId\":\"s1\"}"));
		assertTrue(ack(socket).contains("session.unknown.failed"));
		verifyNoDispatcher(dispatcher);

		org.mockito.Mockito.clearInvocations(socket);
		handler.handleMessage(socket, new TextMessage("{\"type\":\"end\"}"));
		assertTrue(ack(socket).contains("session.end.failed"));

		WebSocketSession anonymous = authenticatedSocket(null);
		handler.handleMessage(anonymous, new TextMessage("{\"type\":\"message\",\"sessionId\":\"s1\"}"));
		assertTrue(ack(anonymous).contains("session.message.failed"));

		WebSocketSession closed = authenticatedSocket("user-1");
		when(closed.isOpen()).thenReturn(false);
		handler.handleMessage(closed, new TextMessage("{\"type\":\"bad\",\"sessionId\":\"s1\"}"));
		verify(closed, never()).sendMessage(any());
	}

	private void verifyNoDispatcher(SessionMessageDispatcher dispatcher) {
		verify(dispatcher, never()).addMessage(any(), any(), any());
		verify(dispatcher, never()).endSession(any(), any(), any());
		verify(dispatcher, never()).bindProviderSession(any(), any(), any());
		verify(dispatcher, never()).activateSession(any(), any());
	}
}
