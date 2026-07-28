package com.unispeaking.infrastructure.config;

import com.unispeaking.websocket.SessionMessageWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class SessionWebSocketConfig implements WebSocketConfigurer {

	private final SessionMessageWebSocketHandler sessionMessageWebSocketHandler;

	public SessionWebSocketConfig(SessionMessageWebSocketHandler sessionMessageWebSocketHandler) {
		this.sessionMessageWebSocketHandler = sessionMessageWebSocketHandler;
	}

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		registry.addHandler(sessionMessageWebSocketHandler, "/ws/session-messages")
				.setAllowedOrigins("*");
	}
}
