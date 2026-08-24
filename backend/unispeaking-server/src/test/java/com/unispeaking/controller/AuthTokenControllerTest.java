package com.unispeaking.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.common.response.ApiResponse;
import com.unispeaking.infrastructure.config.WebOriginProperties;
import com.unispeaking.domain.dto.auth.AuthResponse;
import com.unispeaking.domain.dto.auth.MobileAuthResponse;
import com.unispeaking.domain.dto.auth.UserAccountResponse;
import com.unispeaking.domain.po.auth.UserRole;
import com.unispeaking.domain.po.auth.UserStatus;
import com.unispeaking.domain.vo.auth.IssuedJwt;
import com.unispeaking.service.auth.RefreshTokenService;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthTokenControllerTest {
	@Test
	void refreshWebRotatesCookieAndReturnsAccessResponse() {
		RefreshTokenService service = mock(RefreshTokenService.class);
		WebOriginProperties origins = origins();
		AuthTokenController controller = new AuthTokenController(service, false, origins);
		Instant expiry = Instant.now().plusSeconds(600);
		AuthResponse access = access("web-access", expiry);
		when(service.refresh("web-refresh")).thenReturn(new RefreshTokenService.Result(
				access, "next-refresh", expiry.plusSeconds(100)));
		MockHttpServletRequest request = trustedRequest();
		request.setCookies(new Cookie(AuthTokenController.COOKIE_NAME, "web-refresh"));
		MockHttpServletResponse response = new MockHttpServletResponse();

		var result = controller.refreshWeb(request, response);

		assertEquals(200, result.getStatusCode().value());
		assertEquals(access, result.getBody().data());
		String cookie = response.getHeader(HttpHeaders.SET_COOKIE);
		assertTrue(cookie.contains("us-learning-refresh=next-refresh"));
		assertTrue(cookie.contains("HttpOnly"));
		assertTrue(cookie.contains("SameSite=Lax"));
		assertTrue(cookie.contains("Path=/api/auth/web/token"));
		verify(service).refresh("web-refresh");
	}

	@Test
	void refreshMobileReturnsRotatedAccessAndRefreshTokens() {
		RefreshTokenService service = mock(RefreshTokenService.class);
		AuthTokenController controller = new AuthTokenController(service, false, origins());
		Instant accessExpiry = Instant.now().plusSeconds(300);
		Instant refreshExpiry = Instant.now().plusSeconds(3600);
		AuthResponse access = access("mobile-access", accessExpiry);
		when(service.refresh("mobile-refresh")).thenReturn(new RefreshTokenService.Result(
				access, "mobile-next", refreshExpiry));

		ApiResponse<MobileAuthResponse> result = controller.refreshMobile(
				new com.unispeaking.domain.dto.auth.RefreshTokenRequest("mobile-refresh"));

		assertEquals("mobile-access", result.data().accessToken());
		assertEquals("mobile-next", result.data().refreshToken());
		assertEquals(refreshExpiry, result.data().refreshTokenExpiresAt());
		assertEquals(access.user(), result.data().user());
		verify(service).refresh("mobile-refresh");
	}
	@Test
	void rejectsWebRefreshWithoutTrustedOriginOrCookie() {
		WebOriginProperties origins = new WebOriginProperties();
		origins.setAllowedOriginPatterns(List.of("https://app.example.com"));
		AuthTokenController controller = new AuthTokenController(
				mock(RefreshTokenService.class), false, origins);
		MockHttpServletRequest request = new MockHttpServletRequest();
		assertEquals("AUTH_ORIGIN_INVALID", assertThrows(BusinessException.class,
				() -> controller.refreshWeb(request, new MockHttpServletResponse())).code());
		request.addHeader("Origin", "https://app.example.com");
		assertEquals("REFRESH_TOKEN_INVALID", assertThrows(BusinessException.class,
				() -> controller.refreshWeb(request, new MockHttpServletResponse())).code());
	}

	@Test
	void revokeWebAcceptsRefererAndExpiresCookieWhenNoTokenExists() {
		WebOriginProperties origins = new WebOriginProperties();
		origins.setAllowedOriginPatterns(List.of("https://app.example.com"));
		RefreshTokenService service = mock(RefreshTokenService.class);
		AuthTokenController controller = new AuthTokenController(service, true, origins);
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Referer", "https://app.example.com");
		MockHttpServletResponse response = new MockHttpServletResponse();
		assertEquals(204, controller.revokeWeb(request, response).getStatusCode().value());
		String setCookie = response.getHeader("Set-Cookie");
		org.junit.jupiter.api.Assertions.assertTrue(setCookie.contains("us-learning-refresh="));
		org.junit.jupiter.api.Assertions.assertTrue(setCookie.contains("Max-Age=0"));
		org.junit.jupiter.api.Assertions.assertTrue(setCookie.contains("Secure"));
		org.junit.jupiter.api.Assertions.assertTrue(setCookie.contains("HttpOnly"));
	}

	@Test
	void revokeWebReadsOnlyTheConfiguredCookie() {
		WebOriginProperties origins = new WebOriginProperties();
		origins.setAllowedOriginPatterns(List.of("https://app.example.com"));
		RefreshTokenService service = mock(RefreshTokenService.class);
		AuthTokenController controller = new AuthTokenController(service, false, origins);
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setCookies(new Cookie("other", "value"),
				new Cookie(AuthTokenController.COOKIE_NAME, "token"));
		request.addHeader("Origin", "https://app.example.com");
		MockHttpServletResponse response = new MockHttpServletResponse();
		controller.revokeWeb(request, response);
		org.mockito.Mockito.verify(service).revoke("token");
	}

	@Test
	void revokeMobileDelegatesTokenAndInvalidOriginTakesPrecedenceOverReferer() {
		RefreshTokenService service = mock(RefreshTokenService.class);
		AuthTokenController controller = new AuthTokenController(service, false, origins());
		var result = controller.revokeMobile(new com.unispeaking.domain.dto.auth.RefreshTokenRequest("mobile-token"));
		assertEquals(204, result.getStatusCode().value());
		verify(service).revoke("mobile-token");

		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Origin", "https://evil.example.com");
		request.addHeader("Referer", "https://app.example.com");
		assertEquals("AUTH_ORIGIN_INVALID", assertThrows(BusinessException.class,
				() -> controller.revokeWeb(request, new MockHttpServletResponse())).code());
	}

	@Test
	void acceptsWildcardOriginAndSecureRefreshCookie() {
		RefreshTokenService service = mock(RefreshTokenService.class);
		WebOriginProperties origins = new WebOriginProperties();
		origins.setAllowedOriginPatterns(List.of("https://*.example.com"));
		AuthTokenController controller = new AuthTokenController(service, true, origins);
		Instant expiry = Instant.now().plusSeconds(600);
		when(service.refresh("token")).thenReturn(new RefreshTokenService.Result(
				access("access", expiry), "next", expiry));
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Origin", "https://sub.example.com");
		request.setCookies(new Cookie(AuthTokenController.COOKIE_NAME, "token"));
		MockHttpServletResponse response = new MockHttpServletResponse();

		controller.refreshWeb(request, response);

		assertTrue(response.getHeader(HttpHeaders.SET_COOKIE).contains("Secure"));
	}

	private static WebOriginProperties origins() {
		WebOriginProperties origins = new WebOriginProperties();
		origins.setAllowedOriginPatterns(List.of("https://app.example.com"));
		return origins;
	}

	private static MockHttpServletRequest trustedRequest() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Origin", "https://app.example.com");
		return request;
	}

	private static AuthResponse access(String token, Instant expiry) {
		return new AuthResponse("Bearer", token, expiry, new UserAccountResponse(
				UUID.randomUUID(), "user@example.com", "User", UserRole.USER.name(),
				UserStatus.ACTIVE.name(), Instant.now(), Instant.now()));
	}
}
