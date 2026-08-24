package com.unispeaking.service.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unispeaking.domain.dto.auth.ChangePasswordRequest;
import com.unispeaking.domain.dto.auth.LoginRequest;
import com.unispeaking.domain.dto.auth.RegisterRequest;
import com.unispeaking.domain.po.profile.UserProfile;
import com.unispeaking.domain.po.auth.UserAccount;
import com.unispeaking.domain.po.auth.UserRole;
import com.unispeaking.domain.po.auth.UserStatus;
import com.unispeaking.domain.vo.auth.IssuedJwt;
import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.infrastructure.persistence.repository.user.UserAccountRepository;
import com.unispeaking.infrastructure.persistence.repository.user.UserProfileRepository;
import com.unispeaking.service.auth.impl.AuthServiceImpl;
import com.unispeaking.service.auth.RefreshTokenService;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

	@Mock
	private UserAccountRepository userAccountRepository;
	@Mock
	private UserProfileRepository userProfileRepository;
	@Mock
	private PasswordEncoder passwordEncoder;
	@Mock
	private JwtTokenService jwtTokenService;
	@Mock
	private RefreshTokenService refreshTokenService;

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void registersUserAndCreatesDefaultPreference() {
		AuthServiceImpl service = service();
		when(passwordEncoder.encode("secret12")).thenReturn("bcrypt-hash");
		when(userAccountRepository.create(any(UserAccount.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		when(userProfileRepository.save(any(UserProfile.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		when(jwtTokenService.issue(any(UserAccount.class)))
				.thenReturn(new IssuedJwt("access-token", Instant.parse("2026-07-29T00:00:00Z")));

		var response = service.register(new RegisterRequest("Learner@example.com", "secret12", null));

		assertEquals("access-token", response.accessToken());
		assertEquals("learner@example.com", response.user().username());
		verify(userProfileRepository).save(any(UserProfile.class));
	}

	@Test
	void mapsDuplicateUsernameToStableBusinessError() {
		AuthServiceImpl service = service();
		when(passwordEncoder.encode("secret12")).thenReturn("bcrypt-hash");
		doThrow(new DuplicateKeyException("duplicate username"))
				.when(userAccountRepository).create(any(UserAccount.class));

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> service.register(new RegisterRequest(
						" Learner@Example.com ", "secret12", " Alice ")));

		assertEquals("USERNAME_ALREADY_EXISTS", exception.code());
		verify(userProfileRepository, never()).save(any(UserProfile.class));
	}

	@Test
	void rejectsWrongPassword() {
		AuthServiceImpl service = service();
		UserAccount user = user();
		when(userAccountRepository.findByUsername("learner@example.com"))
				.thenReturn(Optional.of(user));
		when(passwordEncoder.matches("wrong-password", user.passwordHash())).thenReturn(false);

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> service.login(new LoginRequest("learner@example.com", "wrong-password")));

		assertEquals("INVALID_CREDENTIALS", exception.code());
	}

	@Test
	void rejectsMissingAndInactiveUsersDuringLogin() {
		AuthServiceImpl service = service();
		when(userAccountRepository.findByUsername("missing@example.com"))
				.thenReturn(Optional.empty());
		BusinessException missing = assertThrows(
				BusinessException.class,
				() -> service.login(new LoginRequest(" MISSING@example.com ", "secret")));
		assertEquals("INVALID_CREDENTIALS", missing.code());

		UserAccount inactive = new UserAccount(
				user().id(), "learner@example.com", "bcrypt-hash", null,
				UserRole.USER, UserStatus.DISABLED, 0, null,
				Instant.parse("2026-07-28T00:00:00Z"),
				Instant.parse("2026-07-28T00:00:00Z"));
		when(userAccountRepository.findByUsername("learner@example.com"))
				.thenReturn(Optional.of(inactive));
		when(passwordEncoder.matches("secret", inactive.passwordHash())).thenReturn(true);

		BusinessException notActive = assertThrows(
				BusinessException.class,
				() -> service.login(new LoginRequest("learner@example.com", "secret")));
		assertEquals("USER_NOT_ACTIVE", notActive.code());
		verify(userAccountRepository, never()).updateLastLoginAt(any(), any());
	}

	@Test
	void logsInActiveUserAndUpdatesLastLogin() {
		AuthServiceImpl service = service();
		UserAccount user = user();
		when(userAccountRepository.findByUsername(user.username()))
				.thenReturn(Optional.of(user));
		when(passwordEncoder.matches("secret", user.passwordHash())).thenReturn(true);
		when(jwtTokenService.issue(any(UserAccount.class)))
				.thenReturn(new IssuedJwt("login-token", Instant.parse("2026-07-29T00:00:00Z")));

		var response = service.login(new LoginRequest(" LEARNER@EXAMPLE.COM ", "secret"));

		assertEquals("login-token", response.accessToken());
		verify(userAccountRepository).updateLastLoginAt(eq(user.id()), any(Instant.class));
	}

	@Test
	void currentUserRequiresAuthenticationAndReturnsValidatedAccount() {
		AuthServiceImpl service = service();
		BusinessException unauthenticated = assertThrows(
				BusinessException.class, service::currentUser);
		assertEquals("AUTHENTICATION_REQUIRED", unauthenticated.code());

		UserAccount user = user();
		when(userAccountRepository.findById(user.id())).thenReturn(Optional.of(user));
		setAuthentication(user);
		assertEquals(user.username(), service.currentUser().username());
	}

	@Test
	void rejectsMalformedMissingAndRevokedJwtClaims() {
		AuthServiceImpl service = service();
		setAuthentication("not-a-uuid", 0L);
		BusinessException malformed = assertThrows(
				BusinessException.class, service::currentUser);
		assertEquals("INVALID_ACCESS_TOKEN", malformed.code());

		UserAccount user = user();
		when(userAccountRepository.findById(user.id())).thenReturn(Optional.empty());
		setAuthentication(user);
		BusinessException missing = assertThrows(
				BusinessException.class, service::currentUser);
		assertEquals("USER_NOT_FOUND", missing.code());

		when(userAccountRepository.findById(user.id())).thenReturn(Optional.of(user));
		setAuthentication(user, 99L);
		BusinessException revoked = assertThrows(
				BusinessException.class, service::currentUser);
		assertEquals("ACCESS_TOKEN_REVOKED", revoked.code());
	}

	@Test
	void currentUserIdOrNullRejectsNonJwtPrincipals() {
		AuthServiceImpl service = service();
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken("user", "credentials"));
		assertNull(service.currentUserIdOrNull());
	}

	@Test
	void changesPasswordAndRevokesRefreshTokensWhenChecksPass() {
		AuthServiceImpl service = service(refreshTokenService);
		UserAccount user = user();
		when(userAccountRepository.findById(user.id())).thenReturn(Optional.of(user));
		setAuthentication(user);
		when(passwordEncoder.matches("old", user.passwordHash())).thenReturn(true);
		when(passwordEncoder.matches("new", user.passwordHash())).thenReturn(false);
		when(passwordEncoder.encode("new")).thenReturn("new-hash");
		when(userAccountRepository.updatePasswordAndAuthVersion(
				eq(user.id()), eq(user.authVersion()), eq("new-hash"))).thenReturn(true);

		assertNotNull(service.changePassword(new ChangePasswordRequest("old", "new")));
		verify(refreshTokenService).revokeAll(user.id());
	}

	@Test
	void rejectsPasswordValidationAndOptimisticLockFailures() {
		UserAccount user = user();
		when(userAccountRepository.findById(user.id())).thenReturn(Optional.of(user));
		setAuthentication(user);
		when(passwordEncoder.matches("bad", user.passwordHash())).thenReturn(false);
		BusinessException invalid = assertThrows(
				BusinessException.class,
				() -> service().changePassword(new ChangePasswordRequest("bad", "new")));
		assertEquals("CURRENT_PASSWORD_INVALID", invalid.code());

		when(passwordEncoder.matches("old", user.passwordHash())).thenReturn(true);
		BusinessException same = assertThrows(
				BusinessException.class,
				() -> service().changePassword(new ChangePasswordRequest("old", "old")));
		assertEquals("NEW_PASSWORD_SAME_AS_CURRENT", same.code());

		when(passwordEncoder.matches("new", user.passwordHash())).thenReturn(false);
		when(passwordEncoder.encode("new")).thenReturn("new-hash");
		when(userAccountRepository.updatePasswordAndAuthVersion(
				eq(user.id()), eq((long) user.authVersion()), eq("new-hash")))
				.thenReturn(false);
		BusinessException conflict = assertThrows(
				BusinessException.class,
				() -> service().changePassword(new ChangePasswordRequest("old", "new")));
		assertEquals("PASSWORD_UPDATE_CONFLICT", conflict.code());
	}

	@Test
	void resolvesUserIdFromValidatedJwtInsteadOfRequestBody() {
		AuthServiceImpl service = service();
		UserAccount user = user();
		when(userAccountRepository.findById(user.id())).thenReturn(Optional.of(user));
		Jwt jwt = new Jwt(
				"token",
				Instant.parse("2026-07-28T00:00:00Z"),
				Instant.parse("2026-07-29T00:00:00Z"),
				Map.of("alg", "HS256"),
				Map.of("sub", user.id().toString(), "auth_version", 0L));
		SecurityContextHolder.getContext()
				.setAuthentication(new JwtAuthenticationToken(jwt, java.util.List.of()));

		assertEquals(user.id().toString(), service.requireUserId("spoofed-user-id"));
	}

	@Test
	void resolvesOptionalUserOnlyWhenAuthenticationExists() {
		AuthServiceImpl service = service();
		assertNull(service.currentUserIdOrNull());

		UserAccount user = user();
		when(userAccountRepository.findById(user.id())).thenReturn(Optional.of(user));
		setAuthentication(user);

		assertEquals(user.id().toString(), service.currentUserIdOrNull());
	}

	@Test
	void restrictsFeedbackAdministrationToAdminRole() {
		AuthServiceImpl service = service();
		UserAccount user = user();
		when(userAccountRepository.findById(user.id())).thenReturn(Optional.of(user));
		setAuthentication(user);

		BusinessException denied = assertThrows(
				BusinessException.class,
				service::requireAdminUserId);
		assertEquals("ADMIN_ACCESS_DENIED", denied.code());

		UserAccount admin = userWithRole(UserRole.ADMIN);
		when(userAccountRepository.findById(admin.id())).thenReturn(Optional.of(admin));
		setAuthentication(admin);
		assertEquals(admin.id().toString(), service.requireAdminUserId());
	}

	private void setAuthentication(UserAccount user) {
		setAuthentication(user.id().toString(), user.authVersion());
	}

	private void setAuthentication(UserAccount user, Number authVersion) {
		setAuthentication(user.id().toString(), authVersion);
	}

	private void setAuthentication(String subject, Number authVersion) {
		Jwt jwt = new Jwt(
				"token",
				Instant.parse("2026-07-28T00:00:00Z"),
				Instant.parse("2026-07-29T00:00:00Z"),
				Map.of("alg", "HS256"),
				Map.of("sub", subject, "auth_version", authVersion));
		SecurityContextHolder.getContext()
				.setAuthentication(new JwtAuthenticationToken(jwt, java.util.List.of()));
	}

	private AuthServiceImpl service() {
		return service(null);
	}

	private AuthServiceImpl service(RefreshTokenService refreshTokens) {
		return new AuthServiceImpl(
				userAccountRepository,
				userProfileRepository,
				passwordEncoder,
				jwtTokenService,
				refreshTokens);
	}

	private UserAccount user() {
		return userWithRole(UserRole.USER);
	}

	private UserAccount userWithRole(UserRole role) {
		Instant now = Instant.parse("2026-07-28T00:00:00Z");
		return new UserAccount(
				UUID.fromString("22222222-2222-4222-8222-222222222222"),
				"learner@example.com",
				"bcrypt-hash",
				null,
				role,
				UserStatus.ACTIVE,
				0,
				null,
				now,
				now);
	}
}
