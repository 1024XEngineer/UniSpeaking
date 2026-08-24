package com.unispeaking.infrastructure.persistence.repository.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class AuthRefreshTokenRepositoryTest {

	private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
	private final AuthRefreshTokenRepository repository = new AuthRefreshTokenRepository(jdbc);
	private final UUID userId = UUID.randomUUID();
	private final Instant now = Instant.parse("2026-08-21T00:00:00Z");

	@Test
	void insertsTokenWithCreatedAndLastUsedAtSetToTheSameInstant() {
		repository.insert("digest", userId, now, now.plusSeconds(3600));

		verify(jdbc).update(anyString(), eq("digest"), eq(userId),
				eq(Timestamp.from(now)), eq(Timestamp.from(now)),
			eq(Timestamp.from(now.plusSeconds(3600))));
	}

	@Test
	void consumesOnlyAValidAndRecentlyUsedTokenAndReturnsDatabaseCount() {
		when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1, 0);

		assertThat(repository.consume("digest", now, now.minusSeconds(300))).isEqualTo(1);
		assertThat(repository.consume("digest", now, now.minusSeconds(300))).isZero();
		verify(jdbc, org.mockito.Mockito.times(2)).update(anyString(), any(Object[].class));
	}

	@Test
	void mapsFoundTokenIncludingNullableRevocationTime() {
		when(jdbc.query(anyString(), any(RowMapper.class), eq("digest"))).thenAnswer(invocation -> {
			@SuppressWarnings("unchecked")
			RowMapper<AuthRefreshTokenRepository.Record> mapper = invocation.getArgument(1);
			ResultSet result = mock(ResultSet.class);
			when(result.getString("token_digest")).thenReturn("digest");
			when(result.getObject("user_id", UUID.class)).thenReturn(userId);
			when(result.getTimestamp("created_at")).thenReturn(Timestamp.from(now));
			when(result.getTimestamp("last_used_at")).thenReturn(Timestamp.from(now.plusSeconds(1)));
			when(result.getTimestamp("expires_at")).thenReturn(Timestamp.from(now.plusSeconds(3600)));
			when(result.getTimestamp("revoked_at")).thenReturn(null);
			return List.of(mapper.mapRow(result, 0));
		});

		var record = repository.find("digest");

		assertThat(record).isNotNull();
		assertThat(record.digest()).isEqualTo("digest");
		assertThat(record.userId()).isEqualTo(userId);
		assertThat(record.createdAt()).isEqualTo(now);
		assertThat(record.lastUsedAt()).isEqualTo(now.plusSeconds(1));
		assertThat(record.expiresAt()).isEqualTo(now.plusSeconds(3600));
		assertThat(record.revokedAt()).isNull();
	}

	@Test
	void mapsRevokedTokenAndReturnsNullWhenNoRowExists() {
		when(jdbc.query(anyString(), any(RowMapper.class), eq("revoked"))).thenAnswer(invocation -> {
			@SuppressWarnings("unchecked")
			RowMapper<AuthRefreshTokenRepository.Record> mapper = invocation.getArgument(1);
			ResultSet result = mock(ResultSet.class);
			when(result.getString("token_digest")).thenReturn("revoked");
			when(result.getObject("user_id", UUID.class)).thenReturn(userId);
			when(result.getTimestamp("created_at")).thenReturn(Timestamp.from(now));
			when(result.getTimestamp("last_used_at")).thenReturn(Timestamp.from(now));
			when(result.getTimestamp("expires_at")).thenReturn(Timestamp.from(now.plusSeconds(60)));
			when(result.getTimestamp("revoked_at")).thenReturn(Timestamp.from(now.plusSeconds(2)));
			return List.of(mapper.mapRow(result, 0));
		});
		when(jdbc.query(anyString(), any(RowMapper.class), eq("missing"))).thenReturn(List.of());

		assertThat(repository.find("revoked").revokedAt()).isEqualTo(now.plusSeconds(2));
		assertThat(repository.find("missing")).isNull();
	}

	@Test
	void revokesOneTokenOrAllTokensForAUser() {
		repository.revoke("digest", now);
		repository.revokeAll(userId, now);

		verify(jdbc).update(anyString(), eq(Timestamp.from(now)), eq("digest"));
		verify(jdbc).update(anyString(), eq(Timestamp.from(now)), eq(userId));
	}

	@Test
	void deletesAtMostTheExpiredBatchAndReturnsDeletedCount() {
		when(jdbc.update(anyString(), any(Object[].class))).thenReturn(37);

		assertThat(repository.deleteExpired(now)).isEqualTo(37);
		verify(jdbc).update(anyString(), eq(Timestamp.from(now)));
	}
}
