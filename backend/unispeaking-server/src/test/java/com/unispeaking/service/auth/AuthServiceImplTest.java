package com.unispeaking.service.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

	private AuthServiceImpl service() {
		return new AuthServiceImpl(
				userAccountRepository,
				userProfileRepository,
				passwordEncoder,
				jwtTokenService);
	}

	private UserAccount user() {
		Instant now = Instant.parse("2026-07-28T00:00:00Z");
		return new UserAccount(
				UUID.fromString("22222222-2222-4222-8222-222222222222"),
				"learner@example.com",
				"bcrypt-hash",
				null,
				UserRole.USER,
				UserStatus.ACTIVE,
				0,
				null,
				now,
				now);
	}
}
