package com.unispeaking.websocket;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.service.auth.AuthService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Component
public class SessionWebSocketAuthenticationInterceptor implements HandshakeInterceptor {

	public static final String AUTHENTICATED_USER_ID = "authenticatedUserId";

	private final AuthService authService;

	public SessionWebSocketAuthenticationInterceptor(AuthService authService) {
		this.authService = authService;
	}

	@Override
	public boolean beforeHandshake(
			ServerHttpRequest request,
			ServerHttpResponse response,
			WebSocketHandler wsHandler,
			Map<String, Object> attributes) {
		try {
			attributes.put(AUTHENTICATED_USER_ID, authService.requireUserId(null));
			return true;
		}
		catch (BusinessException exception) {
			response.setStatusCode(HttpStatus.UNAUTHORIZED);
			return false;
		}
	}

	@Override
	public void afterHandshake(
			ServerHttpRequest request,
			ServerHttpResponse response,
			WebSocketHandler wsHandler,
			Exception exception) {
	}
}
