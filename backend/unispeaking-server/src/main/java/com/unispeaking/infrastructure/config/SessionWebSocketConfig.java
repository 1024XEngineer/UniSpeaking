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
	private final WebOriginProperties webOriginProperties;

	public SessionWebSocketConfig(
			SessionMessageWebSocketHandler sessionMessageWebSocketHandler,
			SessionWebSocketAuthenticationInterceptor authenticationInterceptor,
			WebOriginProperties webOriginProperties) {
		this.sessionMessageWebSocketHandler = sessionMessageWebSocketHandler;
		this.authenticationInterceptor = authenticationInterceptor;
		this.webOriginProperties = webOriginProperties;
	}

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		registry.addHandler(sessionMessageWebSocketHandler, "/ws/session-messages")
				.addInterceptors(authenticationInterceptor)
				.setAllowedOriginPatterns(webOriginProperties.getAllowedOriginPatterns().toArray(String[]::new));
	}
}
