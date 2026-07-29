package com.unispeaking.infrastructure.config;

import com.unispeaking.websocket.SessionMessageWebSocketHandler;
import com.unispeaking.websocket.SessionWebSocketAuthenticationInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class SessionWebSocketConfig implements WebSocketConfigurer {

	private final SessionMessageWebSocketHandler sessionMessageWebSocketHandler;
	private final SessionWebSocketAuthenticationInterceptor authenticationInterceptor;

	public SessionWebSocketConfig(
			SessionMessageWebSocketHandler sessionMessageWebSocketHandler,
			SessionWebSocketAuthenticationInterceptor authenticationInterceptor) {
		this.sessionMessageWebSocketHandler = sessionMessageWebSocketHandler;
		this.authenticationInterceptor = authenticationInterceptor;
	}

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		registry.addHandler(sessionMessageWebSocketHandler, "/ws/session-messages")
				.addInterceptors(authenticationInterceptor);
	}
}
