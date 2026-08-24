package com.unispeaking.service.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.domain.po.auth.UserAccount;
import com.unispeaking.domain.po.auth.UserRole;
import com.unispeaking.domain.po.auth.UserStatus;
import com.unispeaking.domain.vo.auth.IssuedJwt;
import com.unispeaking.infrastructure.config.JwtProperties;
import com.unispeaking.infrastructure.persistence.repository.auth.AuthRefreshTokenRepository;
import com.unispeaking.infrastructure.persistence.repository.user.UserAccountRepository;
import java.time.Instant;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RefreshTokenServiceTest {
	@Test
	void rejectsUnknownExpiredRevokedAndInactiveTokens() {
		AuthRefreshTokenRepository repository = mock(AuthRefreshTokenRepository.class);
		UserAccountRepository users = mock(UserAccountRepository.class);
		RefreshTokenService service = new RefreshTokenService(repository, users, mock(JwtTokenService.class), properties());
		when(repository.find(any())).thenReturn(null);
		assertInvalid(() -> service.refresh("unknown"));
	}

	@Test
	void rejectsExpiredRevokedAndIdleExpiredRecords() {
		Instant now = Instant.now();
		for (AuthRefreshTokenRepository.Record record : new AuthRefreshTokenRepository.Record[] {
				new AuthRefreshTokenRepository.Record("d", UUID.randomUUID(), now, now,
						now.plusSeconds(60), now.minusSeconds(1)),
				new AuthRefreshTokenRepository.Record("d", UUID.randomUUID(), now, now,
						now.minusSeconds(1), null),
				new AuthRefreshTokenRepository.Record("d", UUID.randomUUID(), now,
						now.minus(Duration.ofDays(8)), now.plusSeconds(60), null) }) {
			AuthRefreshTokenRepository repository = mock(AuthRefreshTokenRepository.class);
			when(repository.find(anyString())).thenReturn(record);
			RefreshTokenService service = new RefreshTokenService(repository,
					mock(UserAccountRepository.class), mock(JwtTokenService.class), properties());
			assertInvalid(() -> service.refresh("raw"));
			verify(repository, never()).consume(anyString(), any(), any());
		}
	}

	@Test
	void refreshesActiveTokenAndRotatesTheDigest() {
		AuthRefreshTokenRepository repository = mock(AuthRefreshTokenRepository.class);
		UserAccountRepository users = mock(UserAccountRepository.class);
		JwtTokenService jwt = mock(JwtTokenService.class);
		UUID id = UUID.randomUUID();
		Instant now = Instant.now();
		when(repository.find(any())).thenReturn(new AuthRefreshTokenRepository.Record(
				"digest", id, now.minusSeconds(1), now.minusSeconds(1), now.plusSeconds(3600), null));
		when(repository.consume(any(), any(), any())).thenReturn(1);
		when(users.findById(id)).thenReturn(Optional.of(user(id, UserStatus.ACTIVE)));
		when(jwt.issue(any())).thenReturn(new IssuedJwt("access", now.plusSeconds(600)));
		RefreshTokenService.Result result = new RefreshTokenService(repository, users, jwt, properties()).refresh("raw");
		assertEquals("access", result.access().accessToken());
		org.mockito.Mockito.verify(repository).consume(any(), any(), any());
	}

	@Test
	void rejectsDisabledUsersAndFailedAtomicConsume() {
		AuthRefreshTokenRepository repository = mock(AuthRefreshTokenRepository.class);
		UserAccountRepository users = mock(UserAccountRepository.class);
		UUID id = UUID.randomUUID();
		Instant now = Instant.now();
		when(repository.find(any())).thenReturn(new AuthRefreshTokenRepository.Record("d", id, now, now, now.plusSeconds(60), null));
		when(users.findById(id)).thenReturn(Optional.of(user(id, UserStatus.DISABLED)));
		RefreshTokenService service = new RefreshTokenService(repository, users, mock(JwtTokenService.class), properties());
		assertInvalid(() -> service.refresh("raw"));
		when(users.findById(id)).thenReturn(Optional.of(user(id, UserStatus.ACTIVE)));
		when(repository.consume(any(), any(), any())).thenReturn(0);
		assertInvalid(() -> service.refresh("raw"));
	}

	@Test
	void rejectsWhenUserCannotBeFoundAndRotatesForMobileClients() {
		AuthRefreshTokenRepository repository = mock(AuthRefreshTokenRepository.class);
		UserAccountRepository users = mock(UserAccountRepository.class);
		JwtTokenService jwt = mock(JwtTokenService.class);
		UUID id = UUID.randomUUID();
		Instant now = Instant.now();
		AuthRefreshTokenRepository.Record record = new AuthRefreshTokenRepository.Record(
				"d", id, now.minusSeconds(10), now.minusSeconds(10), now.plusSeconds(600), null);
		when(repository.find(anyString())).thenReturn(record);
		when(users.findById(id)).thenReturn(Optional.empty());
		RefreshTokenService service = new RefreshTokenService(repository, users, jwt, properties());
		assertInvalid(() -> service.refresh("raw"));

		when(users.findById(id)).thenReturn(Optional.of(user(id, UserStatus.ACTIVE)));
		when(repository.consume(anyString(), any(), any())).thenReturn(1);
		when(jwt.issue(any())).thenReturn(new IssuedJwt("mobile-access", now.plusSeconds(300)));
		RefreshTokenService.MobileResult result = service.refreshMobile("raw");
		assertEquals("mobile-access", result.access().accessToken());
		assertTrue(result.refreshToken() != null && !result.refreshToken().isBlank());
		assertEquals(record.expiresAt(), result.refreshTokenExpiresAt());
		verify(repository).insert(anyString(), org.mockito.ArgumentMatchers.eq(id), any(),
				org.mockito.ArgumentMatchers.eq(record.expiresAt()));
	}

	@Test
	void issuesTokensForIdsAndUsersAndSupportsRevokeAndCleanupOperations() {
		AuthRefreshTokenRepository repository = mock(AuthRefreshTokenRepository.class);
		UUID id = UUID.randomUUID();
		RefreshTokenService service = new RefreshTokenService(repository,
				mock(UserAccountRepository.class), mock(JwtTokenService.class), properties());

		RefreshTokenService.Issued issued = service.issue(id);
		assertTrue(issued.token().length() > 40);
		assertTrue(issued.expiresAt().isAfter(Instant.now()));
		verify(repository).insert(anyString(), org.mockito.ArgumentMatchers.eq(id), any(), any());
		service.issue(user(id, UserStatus.ACTIVE));
		verify(repository, org.mockito.Mockito.times(2)).insert(anyString(),
				org.mockito.ArgumentMatchers.eq(id), any(), any());

		service.revoke(null);
		service.revoke("  ");
		verify(repository, never()).revoke(anyString(), any());
		service.revoke("raw-token");
		verify(repository).revoke(anyString(), any());
		service.revokeAll(id);
		verify(repository).revokeAll(org.mockito.ArgumentMatchers.eq(id), any());
		when(repository.deleteExpired(any())).thenReturn(4);
		assertEquals(4, service.cleanup());
		verify(repository).deleteExpired(any());
	}

	@Test
	void rejectsInvalidRefreshTtlConfiguration() {
		JwtProperties idleZero = properties();
		idleZero.setRefreshIdleTtl(java.time.Duration.ZERO);
		assertThrows(IllegalStateException.class, () -> new RefreshTokenService(
				mock(AuthRefreshTokenRepository.class), mock(UserAccountRepository.class),
				mock(JwtTokenService.class), idleZero));

		JwtProperties idleTooLong = properties();
		idleTooLong.setRefreshIdleTtl(java.time.Duration.ofDays(31));
		assertThrows(IllegalStateException.class, () -> new RefreshTokenService(
				mock(AuthRefreshTokenRepository.class), mock(UserAccountRepository.class),
				mock(JwtTokenService.class), idleTooLong));

		JwtProperties absoluteZero = properties();
		absoluteZero.setRefreshAbsoluteTtl(java.time.Duration.ZERO);
		assertThrows(IllegalStateException.class, () -> new RefreshTokenService(
				mock(AuthRefreshTokenRepository.class), mock(UserAccountRepository.class),
				mock(JwtTokenService.class), absoluteZero));
	}

	private static void assertInvalid(org.junit.jupiter.api.function.Executable action) {
		assertEquals("REFRESH_TOKEN_INVALID", assertThrows(BusinessException.class, action).code());
	}
	private static JwtProperties properties() {
		JwtProperties p = new JwtProperties();
		p.setRefreshIdleTtl(java.time.Duration.ofDays(7));
		p.setRefreshAbsoluteTtl(java.time.Duration.ofDays(30));
		return p;
	}
	private static UserAccount user(UUID id, UserStatus status) {
		return new UserAccount(id, "user@example.com", "hash", "User", UserRole.USER, status, 0, null, Instant.now(), Instant.now());
	}
}
