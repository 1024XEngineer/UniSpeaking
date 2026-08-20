package com.unispeaking.service.auth.impl;

import com.unispeaking.domain.dto.auth.AuthResponse;
import com.unispeaking.domain.dto.auth.ChangePasswordRequest;
import com.unispeaking.domain.dto.auth.ChangePasswordResponse;
import com.unispeaking.domain.dto.auth.LoginRequest;
import com.unispeaking.domain.dto.auth.RegisterRequest;
import com.unispeaking.domain.dto.auth.UserAccountResponse;
import com.unispeaking.domain.po.profile.UserProfile;
import com.unispeaking.domain.po.auth.UserAccount;
import com.unispeaking.domain.po.auth.UserRole;
import com.unispeaking.domain.po.auth.UserStatus;
import com.unispeaking.domain.vo.auth.IssuedJwt;
import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.infrastructure.persistence.repository.user.UserAccountRepository;
import com.unispeaking.infrastructure.persistence.repository.user.UserProfileRepository;
import com.unispeaking.service.auth.AuthService;
import com.unispeaking.service.auth.JwtTokenService;
import com.unispeaking.service.auth.RefreshTokenService;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class AuthServiceImpl implements AuthService {

	private final UserAccountRepository userAccountRepository;
	private final UserProfileRepository userProfileRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenService jwtTokenService;
	private final RefreshTokenService refreshTokenService;

	@Autowired
	public AuthServiceImpl(
			UserAccountRepository userAccountRepository,
			UserProfileRepository userProfileRepository,
			PasswordEncoder passwordEncoder,
			JwtTokenService jwtTokenService,
			RefreshTokenService refreshTokenService) {
		this.userAccountRepository = userAccountRepository;
		this.userProfileRepository = userProfileRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtTokenService = jwtTokenService;
		this.refreshTokenService = refreshTokenService;
	}

	public AuthServiceImpl(
			UserAccountRepository userAccountRepository,
			UserProfileRepository userProfileRepository,
			PasswordEncoder passwordEncoder,
			JwtTokenService jwtTokenService) {
		this(userAccountRepository, userProfileRepository, passwordEncoder, jwtTokenService, null);
	}

	@Override
	@Transactional
	public AuthResponse register(RegisterRequest request) {
		String username = normalizeUsername(request.username());
		Instant now = Instant.now();
		UserAccount user = new UserAccount(
				UUID.randomUUID(),
				username,
				passwordEncoder.encode(request.password()),
				normalizeNullable(request.nickname()),
				UserRole.USER,
				UserStatus.ACTIVE,
				0,
				null,
				now,
				now);
		try {
			userAccountRepository.create(user);
			userProfileRepository.save(new UserProfile(
					user.id().toString(),
					null,
					null,
					"NATURAL",
					"zh-CN",
					""));
		}
		catch (DuplicateKeyException exception) {
			throw new BusinessException("USERNAME_ALREADY_EXISTS", "该邮箱已注册");
		}
		return createAuthResponse(user);
	}

	@Override
	@Transactional
	public AuthResponse login(LoginRequest request) {
		String username = normalizeUsername(request.username());
		UserAccount user = userAccountRepository.findByUsername(username)
				.orElseThrow(this::invalidCredentials);
		if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
			throw invalidCredentials();
		}
		if (user.status() != UserStatus.ACTIVE) {
			throw new BusinessException("USER_NOT_ACTIVE", "账号当前不可登录");
		}
		Instant lastLoginAt = Instant.now();
		userAccountRepository.updateLastLoginAt(user.id(), lastLoginAt);
		return createAuthResponse(user.withLastLoginAt(lastLoginAt));
	}

	@Override
	public UserAccountResponse currentUser() {
		return UserAccountResponse.from(requireAuthenticatedUser());
	}

	@Override
	@Transactional
	public ChangePasswordResponse changePassword(ChangePasswordRequest request) {
		UserAccount user = requireAuthenticatedUser();
		if (!passwordEncoder.matches(request.currentPassword(), user.passwordHash())) {
			throw new BusinessException("CURRENT_PASSWORD_INVALID", "当前密码不正确");
		}
		if (passwordEncoder.matches(request.newPassword(), user.passwordHash())) {
			throw new BusinessException("NEW_PASSWORD_SAME_AS_CURRENT", "新密码不能与当前密码相同");
		}
		String encoded = passwordEncoder.encode(request.newPassword());
		if (!userAccountRepository.updatePasswordAndAuthVersion(
				user.id(), user.authVersion(), encoded)) {
			throw new BusinessException("PASSWORD_UPDATE_CONFLICT", "账号已发生变化，请重新登录后再试");
		}
		if (refreshTokenService != null) {
			refreshTokenService.revokeAll(user.id());
		}
		return ChangePasswordResponse.required();
	}

	@Override
	public String currentUserIdOrNull() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null
				|| !authentication.isAuthenticated()
				|| !(authentication.getPrincipal() instanceof Jwt)) {
			return null;
		}
		return requireAuthenticatedUser().id().toString();
	}

	@Override
	public String requireUserId(String requestedUserId) {
		return requireAuthenticatedUser().id().toString();
	}

	@Override
	public String requireAdminUserId() {
		UserAccount user = requireAuthenticatedUser();
		if (user.role() != UserRole.ADMIN) {
			throw new BusinessException("ADMIN_ACCESS_DENIED", "当前账号没有反馈处理权限");
		}
		return user.id().toString();
	}

	private UserAccount requireAuthenticatedUser() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null
				|| !authentication.isAuthenticated()
				|| !(authentication.getPrincipal() instanceof Jwt jwt)
				|| jwt.getSubject() == null) {
			throw new BusinessException("AUTHENTICATION_REQUIRED", "请先登录");
		}
		UUID userId;
		try {
			userId = UUID.fromString(jwt.getSubject());
		}
		catch (IllegalArgumentException exception) {
			throw new BusinessException("INVALID_ACCESS_TOKEN", "Access Token 中的用户标识无效");
		}
		UserAccount user = userAccountRepository.findById(userId)
				.orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "用户不存在"));
		Number tokenAuthVersion = jwt.getClaim("auth_version");
		if (user.status() != UserStatus.ACTIVE
				|| tokenAuthVersion == null
				|| tokenAuthVersion.longValue() != user.authVersion()) {
			throw new BusinessException("ACCESS_TOKEN_REVOKED", "登录状态已失效，请重新登录");
		}
		return user;
	}

	private AuthResponse createAuthResponse(UserAccount user) {
		IssuedJwt issuedJwt = jwtTokenService.issue(user);
		return new AuthResponse(
				"Bearer",
				issuedJwt.token(),
				issuedJwt.expiresAt(),
				UserAccountResponse.from(user));
	}

	private String normalizeUsername(String username) {
		return username.trim().toLowerCase(Locale.ROOT);
	}

	private String normalizeNullable(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}

	private BusinessException invalidCredentials() {
		return new BusinessException("INVALID_CREDENTIALS", "邮箱或密码错误");
	}
}
