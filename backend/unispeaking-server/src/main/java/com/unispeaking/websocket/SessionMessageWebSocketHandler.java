package com.unispeaking.websocket;

import com.unispeaking.common.logging.RealtimeFlowLog;
import com.unispeaking.component.session.SessionMessageDispatcher;
import com.unispeaking.domain.dto.session.SessionSocketAck;
import com.unispeaking.domain.dto.session.SessionSocketMessage;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

@Component
public class SessionMessageWebSocketHandler extends TextWebSocketHandler {

	private final ObjectMapper objectMapper;
	private final SessionMessageDispatcher sessionDispatcher;

	public SessionMessageWebSocketHandler(
			ObjectMapper objectMapper,
			SessionMessageDispatcher sessionDispatcher) {
		this.objectMapper = objectMapper;
		this.sessionDispatcher = sessionDispatcher;
	}

	@Override
	protected void handleTextMessage(WebSocketSession webSocketSession, TextMessage message) throws Exception {
		SessionSocketMessage frame = objectMapper.readValue(message.getPayload(), SessionSocketMessage.class);
		try {
			handleFrame(webSocketSession, frame);
		}
		catch (RuntimeException exception) {
			send(webSocketSession, SessionSocketAck.failure(
					ackType(frame.type(), "failed"),
					frame.sessionId(),
					"SESSION_SOCKET_ERROR",
					exception.getMessage()));
		}
	}

	private void handleFrame(WebSocketSession webSocketSession, SessionSocketMessage frame) throws IOException {
		String sessionId = requireSessionId(frame.sessionId());
		String userId = requireAuthenticatedUserId(webSocketSession);
		switch (normalize(frame.type())) {
			case "message" -> {
				sessionDispatcher.addMessage(userId, sessionId, frame.message());
				RealtimeFlowLog.info("session.websocket.message sessionId={} owner={} content={} audioBytes={}",
						sessionId,
						frame.message() == null ? null : frame.message().owner(),
						frame.message() == null ? null : RealtimeFlowLog.textSummary(frame.message().content()),
						frame.message() == null || frame.message().audio() == null
								? 0 : frame.message().audio().length);
				send(webSocketSession, SessionSocketAck.success("session.message.accepted", sessionId, null));
			}
			case "end" -> {
				sessionDispatcher.endSession(userId, sessionId, frame.stopTime());
				RealtimeFlowLog.info("session.websocket.end sessionId={} stopTime={}",
						sessionId, frame.stopTime());
				send(webSocketSession, SessionSocketAck.success("session.end.accepted", sessionId, null));
			}
			case "bind" -> {
				sessionDispatcher.bindProviderSession(userId, sessionId, requireProviderSessionId(frame.providerSessionId()));
				send(webSocketSession, SessionSocketAck.success("session.bind.accepted", sessionId, null));
			}
			default -> throw new IllegalArgumentException("unsupported session socket type: " + frame.type());
		}
	}

	private String requireAuthenticatedUserId(WebSocketSession session) {
		Object value = session.getAttributes()
				.get(SessionWebSocketAuthenticationInterceptor.AUTHENTICATED_USER_ID);
		if (!(value instanceof String userId) || userId.isBlank()) {
			throw new IllegalStateException("authenticated WebSocket user is required");
		}
		return userId;
	}

	private String normalize(String type) {
		if (type == null || type.isBlank()) {
			return "message";
		}
		return switch (type.trim()) {
			case "message", "session.message", "addMessage" -> "message";
			case "end", "session.end", "endSession" -> "end";
			case "bind", "session.bind", "bindProviderSession" -> "bind";
			default -> type.trim();
		};
	}

	private String requireSessionId(String sessionId) {
		if (sessionId == null || sessionId.isBlank()) {
			throw new IllegalArgumentException("sessionId is required");
		}
		return sessionId.trim();
	}

	private String requireProviderSessionId(String providerSessionId) {
		if (providerSessionId == null || providerSessionId.isBlank()) {
			throw new IllegalArgumentException("providerSessionId is required");
		}
		return providerSessionId.trim();
	}

	private String ackType(String type, String suffix) {
		String normalized = normalize(type);
		return "session." + normalized + "." + suffix;
	}

	private void send(WebSocketSession session, SessionSocketAck ack) throws IOException {
		if (session.isOpen()) {
			session.sendMessage(new TextMessage(objectMapper.writeValueAsString(ack)));
		}
	}
}
