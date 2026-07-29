package com.unispeaking.websocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.unispeaking.domain.dto.auth.AuthResponse;
import com.unispeaking.domain.dto.auth.LoginRequest;
import com.unispeaking.domain.dto.auth.RegisterRequest;
import com.unispeaking.domain.dto.auth.UserAccountResponse;
import com.unispeaking.exception.BusinessException;
import com.unispeaking.service.auth.AuthService;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.socket.handler.TextWebSocketHandler;

class SessionWebSocketAuthenticationInterceptorTest {

	@Test
	void storesValidatedUserIdInHandshakeAttributes() {
		var interceptor = new SessionWebSocketAuthenticationInterceptor(new FixedAuthService("user-1"));
		var attributes = new HashMap<String, Object>();
		var request = request();
		var response = response();

		assertTrue(interceptor.beforeHandshake(
				request,
				response,
				new TextWebSocketHandler(),
				attributes));
		assertEquals(
				"user-1",
				attributes.get(SessionWebSocketAuthenticationInterceptor.AUTHENTICATED_USER_ID));
	}

	@Test
	void rejectsHandshakeWhenAuthServiceRejectsToken() {
		var interceptor = new SessionWebSocketAuthenticationInterceptor(new RejectingAuthService());
		var servletResponse = new MockHttpServletResponse();
		var response = new ServletServerHttpResponse(servletResponse);

		assertFalse(interceptor.beforeHandshake(
				request(),
				response,
				new TextWebSocketHandler(),
				new HashMap<>()));
		assertEquals(HttpStatus.UNAUTHORIZED.value(), servletResponse.getStatus());
	}

	private static final class FixedAuthService extends StubAuthService {
		private final String userId;

		private FixedAuthService(String userId) {
			this.userId = userId;
		}

		@Override
		public String requireUserId(String requestedUserId) {
			return userId;
		}
	}

	private static final class RejectingAuthService extends StubAuthService {
		@Override
		public String requireUserId(String requestedUserId) {
			throw new BusinessException("AUTHENTICATION_REQUIRED", "请先登录");
		}
	}

	private abstract static class StubAuthService implements AuthService {
		@Override
		public AuthResponse register(RegisterRequest request) {
			throw new UnsupportedOperationException();
		}

		@Override
		public AuthResponse login(LoginRequest request) {
			throw new UnsupportedOperationException();
		}

		@Override
		public UserAccountResponse currentUser() {
			throw new UnsupportedOperationException();
		}
	}

	private ServletServerHttpRequest request() {
		return new ServletServerHttpRequest(
				new MockHttpServletRequest("GET", "/ws/session-messages"));
	}

	private ServletServerHttpResponse response() {
		return new ServletServerHttpResponse(new MockHttpServletResponse());
	}
}
