package com.unispeaking.controller;

import com.unispeaking.common.exception.EmailAuthException;
import com.unispeaking.common.response.ApiResponse;
import com.unispeaking.domain.dto.auth.AuthResponse;
import com.unispeaking.domain.dto.auth.ChangePasswordRequest;
import com.unispeaking.domain.dto.auth.ChangePasswordResponse;
import com.unispeaking.domain.dto.auth.LoginRequest;
import com.unispeaking.domain.dto.auth.RegisterRequest;
import com.unispeaking.domain.dto.auth.UserAccountResponse;
import com.unispeaking.service.auth.AuthService;
import com.unispeaking.service.auth.EmailAuthService;
import com.unispeaking.service.auth.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.validation.Valid;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;
	private final EmailAuthService emailAuthService;
	private final RefreshTokenService refreshTokenService;
	private final boolean secureCookie;

	@Autowired
	public AuthController(AuthService authService, EmailAuthService emailAuthService, RefreshTokenService refreshTokenService,
			@Value("${AUTH_COOKIE_SECURE:false}") boolean secureCookie) {
		this.authService = authService;
		this.emailAuthService = emailAuthService;
		this.refreshTokenService = refreshTokenService;
		this.secureCookie = secureCookie;
	}

	public AuthController(AuthService authService, EmailAuthService emailAuthService) {
		this(authService, emailAuthService, null, false);
	}

	@PostMapping("/register")
	public ApiResponse<AuthResponse> register(
			@Valid @RequestBody RegisterRequest request,
			HttpServletRequest servletRequest,
			HttpServletResponse servletResponse) {
		requireVerifiedEmail(request.username(), servletRequest);
		AuthResponse auth = authService.register(request);
		addLearningRefreshCookie(servletResponse, auth);
		return ApiResponse.success(auth);
	}

	@PostMapping("/login")
	public ApiResponse<AuthResponse> login(
			@Valid @RequestBody LoginRequest request,
			HttpServletRequest servletRequest,
			HttpServletResponse servletResponse) {
		requireVerifiedEmail(request.username(), servletRequest);
		AuthResponse auth = authService.login(request);
		addLearningRefreshCookie(servletResponse, auth);
		return ApiResponse.success(auth);
	}

	private void addLearningRefreshCookie(HttpServletResponse response, AuthResponse auth) {
		if (refreshTokenService == null || auth == null || auth.user() == null) {
			return;
		}
		var issued = refreshTokenService.issue(auth.user().id());
		response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(AuthTokenController.COOKIE_NAME, issued.token())
				.httpOnly(true)
				.secure(secureCookie)
				.sameSite("Lax")
				.path("/api/auth/web/token")
				.build()
				.toString());
	}

	private void requireVerifiedEmail(String username, HttpServletRequest request) {
		var verifiedUser = emailAuthService.currentUser(readEmailSession(request));
		if (!verifiedUser.email().equalsIgnoreCase(username.trim())) {
			throw new EmailAuthException("HUMAN_VERIFICATION_REQUIRED");
		}
	}

	private static String readEmailSession(HttpServletRequest request) {
		if (request.getCookies() != null) {
			for (var cookie : request.getCookies()) {
				if (UserAuthController.COOKIE_NAME.equals(cookie.getName())
						&& StringUtils.hasText(cookie.getValue())) {
					return cookie.getValue();
				}
			}
		}
		throw new EmailAuthException("HUMAN_VERIFICATION_REQUIRED");
	}

	@GetMapping("/me")
	public ApiResponse<UserAccountResponse> me() {
		return ApiResponse.success(authService.currentUser());
	}

	@PutMapping("/password")
	public ApiResponse<ChangePasswordResponse> changePassword(
			@Valid @RequestBody ChangePasswordRequest request) {
		return ApiResponse.success(authService.changePassword(request));
	}
}
