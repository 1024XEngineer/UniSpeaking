package com.unispeaking.service.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unispeaking.domain.dto.account.ReactivateAccountRequest;
import com.unispeaking.domain.dto.auth.LoginRequest;
import com.unispeaking.domain.dto.auth.RegisterRequest;
import com.unispeaking.domain.po.profile.UserProfile;
import com.unispeaking.domain.po.user.UserAccount;
import com.unispeaking.domain.po.user.UserRole;
import com.unispeaking.domain.po.user.UserStatus;
import com.unispeaking.domain.vo.auth.IssuedJwt;
import com.unispeaking.exception.BusinessException;
import com.unispeaking.repository.UserAccountRepository;
import com.unispeaking.repository.UserProfileRepository;
import com.unispeaking.service.account.AvatarUrlResolver;
import com.unispeaking.service.auth.impl.AuthServiceImpl;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

	private static final Instant NOW = Instant.parse("2026-07-30T04:00:00Z");

	@Mock
	private UserAccountRepository userAccountRepository;
	@Mock
	private UserProfileRepository userProfileRepository;
	@Mock
	private PasswordEncoder passwordEncoder;
	@Mock
	private JwtTokenService jwtTokenService;
	@Mock
	private AvatarUrlResolver avatarUrlResolver;

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
	void reactivatesPendingAccountBeforeDeletionDeadline() {
		UserAccount pending = pendingUser(NOW.plusSeconds(60));
		when(userAccountRepository.findByUsername("learner@example.com"))
				.thenReturn(Optional.of(pending));
		when(passwordEncoder.matches("secret12", pending.passwordHash())).thenReturn(true);
		UserAccount active = userWithAuthVersion(3);
		when(userAccountRepository.reactivate(pending.id(), 3)).thenReturn(active);
		when(jwtTokenService.issue(active))
				.thenReturn(new IssuedJwt("reactivated-token", NOW.plusSeconds(3600)));

		var response = service().reactivate(new ReactivateAccountRequest(
				"learner@example.com",
				"secret12"));

		assertEquals("reactivated-token", response.accessToken());
		verify(userAccountRepository).reactivate(pending.id(), 3);
	}

	@Test
	void rejectsReactivationAtDeletionDeadline() {
		UserAccount pending = pendingUser(NOW);
		when(userAccountRepository.findByUsername("learner@example.com"))
				.thenReturn(Optional.of(pending));
		when(passwordEncoder.matches("secret12", pending.passwordHash())).thenReturn(true);

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> service().reactivate(new ReactivateAccountRequest(
						"learner@example.com",
						"secret12")));

		assertEquals("ACCOUNT_REACTIVATION_NOT_ALLOWED", exception.code());
	}

	private AuthServiceImpl service() {
		return new AuthServiceImpl(
				userAccountRepository,
				userProfileRepository,
				passwordEncoder,
				jwtTokenService,
				avatarUrlResolver,
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private UserAccount user() {
		return userWithAuthVersion(0);
	}

	private UserAccount userWithAuthVersion(long authVersion) {
		return new UserAccount(
				UUID.fromString("22222222-2222-4222-8222-222222222222"),
				"learner@example.com",
				"bcrypt-hash",
				null,
				null,
				UserRole.USER,
				UserStatus.ACTIVE,
				authVersion,
				null,
				null,
				null,
				NOW,
				NOW);
	}

	private UserAccount pendingUser(Instant scheduledAt) {
		return new UserAccount(
				UUID.fromString("22222222-2222-4222-8222-222222222222"),
				"learner@example.com",
				"bcrypt-hash",
				null,
				null,
				UserRole.USER,
				UserStatus.PENDING_DELETION,
				2,
				null,
				NOW.minusSeconds(60),
				scheduledAt,
				NOW.minusSeconds(3600),
				NOW);
	}
}
