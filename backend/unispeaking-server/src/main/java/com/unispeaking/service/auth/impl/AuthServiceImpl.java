package com.unispeaking.service.auth.impl;

import com.unispeaking.domain.dto.account.ReactivateAccountRequest;
import com.unispeaking.domain.dto.auth.AuthResponse;
import com.unispeaking.domain.dto.auth.LoginRequest;
import com.unispeaking.domain.dto.auth.RegisterRequest;
import com.unispeaking.domain.dto.auth.UserAccountResponse;
import com.unispeaking.domain.po.profile.UserProfile;
import com.unispeaking.domain.po.user.UserAccount;
import com.unispeaking.domain.po.user.UserRole;
import com.unispeaking.domain.po.user.UserStatus;
import com.unispeaking.domain.vo.auth.IssuedJwt;
import com.unispeaking.exception.BusinessException;
import com.unispeaking.repository.UserAccountRepository;
import com.unispeaking.repository.UserProfileRepository;
import com.unispeaking.service.account.AvatarUrlResolver;
import com.unispeaking.service.auth.AuthService;
import com.unispeaking.service.auth.JwtTokenService;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {

	private final UserAccountRepository userAccountRepository;
	private final UserProfileRepository userProfileRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenService jwtTokenService;
	private final AvatarUrlResolver avatarUrlResolver;
	private final Clock clock;

	@Autowired
	public AuthServiceImpl(
			UserAccountRepository userAccountRepository,
			UserProfileRepository userProfileRepository,
			PasswordEncoder passwordEncoder,
			JwtTokenService jwtTokenService,
			AvatarUrlResolver avatarUrlResolver) {
		this(
				userAccountRepository,
				userProfileRepository,
				passwordEncoder,
				jwtTokenService,
				avatarUrlResolver,
				Clock.systemUTC());
	}

	public AuthServiceImpl(
			UserAccountRepository userAccountRepository,
			UserProfileRepository userProfileRepository,
			PasswordEncoder passwordEncoder,
			JwtTokenService jwtTokenService,
			AvatarUrlResolver avatarUrlResolver,
			Clock clock) {
		this.userAccountRepository = userAccountRepository;
		this.userProfileRepository = userProfileRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtTokenService = jwtTokenService;
		this.avatarUrlResolver = avatarUrlResolver;
		this.clock = clock;
	}

	@Override
	@Transactional
	public AuthResponse register(RegisterRequest request) {
		String username = normalizeUsername(request.username());
		Instant now = clock.instant();
		UserAccount user = new UserAccount(
				UUID.randomUUID(),
				username,
				passwordEncoder.encode(request.password()),
				normalizeNullable(request.nickname()),
				null,
				UserRole.USER,
				UserStatus.ACTIVE,
				0,
				null,
				null,
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
		if (user.status() == UserStatus.PENDING_DELETION) {
			throw new BusinessException(
					"ACCOUNT_PENDING_DELETION",
					"账号处于待删除状态，请先撤销注销");
		}
		if (user.status() != UserStatus.ACTIVE) {
			throw new BusinessException("USER_NOT_ACTIVE", "账号当前不可登录");
		}
		Instant lastLoginAt = clock.instant();
		userAccountRepository.updateLastLoginAt(user.id(), lastLoginAt);
		return createAuthResponse(user.withLastLoginAt(lastLoginAt));
	}

	@Override
	@Transactional
	public AuthResponse reactivate(ReactivateAccountRequest request) {
		String username = normalizeUsername(request.username());
		UserAccount user = userAccountRepository.findByUsername(username)
				.orElseThrow(this::invalidCredentials);
		if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
			throw invalidCredentials();
		}
		Instant scheduledAt = user.deletionScheduledAt();
		if (user.status() != UserStatus.PENDING_DELETION
				|| scheduledAt == null
				|| !clock.instant().isBefore(scheduledAt)) {
			throw new BusinessException(
					"ACCOUNT_REACTIVATION_NOT_ALLOWED",
					"账号当前无法撤销注销");
		}
		UserAccount reactivated = userAccountRepository.reactivate(
				user.id(),
				user.authVersion() + 1);
		return createAuthResponse(reactivated);
	}

	@Override
	public UserAccountResponse currentUser() {
		UserAccount user = requireAuthenticatedUser();
		return UserAccountResponse.from(
				user,
				avatarUrlResolver.resolve(user.avatarObjectKey()));
	}

	@Override
	public String requireUserId(String requestedUserId) {
		return requireAuthenticatedUser().id().toString();
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
				UserAccountResponse.from(
						user,
						avatarUrlResolver.resolve(user.avatarObjectKey())));
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
