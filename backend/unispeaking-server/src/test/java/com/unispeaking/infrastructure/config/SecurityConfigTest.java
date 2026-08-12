package com.unispeaking.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import com.unispeaking.admin.auth.security.SessionAuthenticationFilter;

class SecurityConfigTest {

	private final SecurityConfig securityConfig = new SecurityConfig();

	@Test
	void acceptsQueryBearerTokenOnlyForSessionWebSocketHandshake() {
		var resolver = securityConfig.bearerTokenResolver();
		var webSocketRequest = new MockHttpServletRequest("GET", "/ws/session-messages");
		webSocketRequest.setParameter("access_token", "signed-token");
		var apiRequest = new MockHttpServletRequest("GET", "/api/user-preferences");
		apiRequest.setParameter("access_token", "signed-token");

		assertEquals("signed-token", resolver.resolve(webSocketRequest));
		assertNull(resolver.resolve(apiRequest));
	}

	@Test
	void continuesToAcceptAuthorizationHeader() {
		var request = new MockHttpServletRequest("GET", "/api/user-preferences");
		request.addHeader("Authorization", "Bearer header-token");

		assertEquals("header-token", securityConfig.bearerTokenResolver().resolve(request));
	}

	@Test
	void adminSessionCookieIsScopedToAdminRoutes() {
		assertEquals(true, SessionAuthenticationFilter.isAdminRoute("/api/admin/auth/me"));
		assertEquals(false, SessionAuthenticationFilter.isAdminRoute("/api/user-preferences"));
	}
}
