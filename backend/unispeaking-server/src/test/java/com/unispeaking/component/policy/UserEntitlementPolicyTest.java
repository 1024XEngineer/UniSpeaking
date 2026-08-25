package com.unispeaking.component.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.unispeaking.common.exception.BusinessException;
import java.util.UUID;
import java.time.Instant;
import java.time.LocalDate;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class UserEntitlementPolicyTest {
	@Test
	void coversNullDependenciesInvalidIdentifiersMissingRowsAndGuardClauses() {
		var disabled = new UserEntitlementPolicy(null);
		disabled.assertAllowed("anything");
		assertNull(disabled.reserveRemaining("anything", Instant.now()));
		assertNull(disabled.reserveRemaining("anything", null));
		disabled.settleReservation("anything", null, 1, Instant.now(), Instant.now());
		disabled.settleReservation("anything", LocalDate.now(), 0, Instant.now(), Instant.now());
		disabled.settleReservation("anything", LocalDate.now(), 1, null, Instant.now());
		disabled.settleReservation("anything", LocalDate.now(), 1, Instant.now(), null);
		disabled.recordUsage("anything", null, Instant.now());
		disabled.recordUsage("anything", Instant.now(), null);
		disabled.recordUsage("anything", Instant.now(), Instant.EPOCH);

		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		var policy = new UserEntitlementPolicy(jdbc);
		assertEquals("INVALID_USER_ID", assertThrows(BusinessException.class, () -> policy.assertAllowed("bad")).code());
		assertEquals("INVALID_USER_ID", assertThrows(BusinessException.class, () -> policy.reserveRemaining(null, Instant.now())).code());
		assertEquals("INVALID_USER_ID", assertThrows(BusinessException.class, () -> policy.recordUsage("bad", Instant.EPOCH, Instant.now())).code());
		when(jdbc.queryForObject(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(UUID.class))).thenReturn(null);
		String user = UUID.randomUUID().toString();
		policy.assertAllowed(user);
		assertNull(policy.reserveRemaining(user, Instant.now()));
	}

    @Test
    void blocksSuspendedAndExhaustedAccounts() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:user-entitlement-policy;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("create table user_entitlements (user_id uuid, quota_date date, quota_seconds numeric(12,3), used_seconds numeric(12,3), status varchar(32), updated_at timestamp with time zone)");
        UUID suspended = UUID.randomUUID();
        UUID exhausted = UUID.randomUUID();
        jdbc.update("insert into user_entitlements values (?, current_date, 600, 0, 'suspended', current_timestamp)", suspended);
        jdbc.update("insert into user_entitlements values (?, current_date, 600, 600, 'active', current_timestamp)", exhausted);
        var policy = new UserEntitlementPolicy(jdbc);

        assertEquals("USER_ENTITLEMENT_SUSPENDED", assertThrows(BusinessException.class,
                () -> policy.assertAllowed(suspended.toString())).code());
        assertEquals("USER_QUOTA_EXHAUSTED", assertThrows(BusinessException.class,
                () -> policy.assertAllowed(exhausted.toString())).code());
    }

    @Test
    void recordsCompletedPracticeDurationInTheDailyLedger() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:user-entitlement-usage;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("create table user_entitlements (user_id uuid, quota_date date, quota_seconds numeric(12,3), used_seconds numeric(12,3), status varchar(32), updated_at timestamp with time zone)");
        UUID userId = UUID.randomUUID();
        jdbc.update("insert into user_entitlements values (?, current_date, 600, 20, 'active', current_timestamp)", userId);
        var policy = new UserEntitlementPolicy(jdbc);

        policy.recordUsage(userId.toString(), Instant.parse("2026-08-11T00:00:00Z"), Instant.parse("2026-08-11T00:01:30Z"));

        assertEquals(110d, jdbc.queryForObject(
                "select used_seconds from user_entitlements where user_id = ?", Double.class, userId));
    }

    @Test
    void reservesTheRemainingQuotaAndRefundsUnusedSeconds() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:user-entitlement-reservation;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("create table user_entitlements (user_id uuid, quota_date date, quota_seconds numeric(12,3), used_seconds numeric(12,3), status varchar(32), updated_at timestamp with time zone)");
        UUID userId = UUID.randomUUID();
        jdbc.update("insert into user_entitlements values (?, current_date, 600, 590, 'active', current_timestamp)", userId);
        var policy = new UserEntitlementPolicy(jdbc);
        Instant startedAt = Instant.parse("2026-08-24T05:00:00Z");

        var reservation = policy.reserveRemaining(userId.toString(), startedAt);

        assertEquals(10d, reservation.reservedSeconds());
        assertEquals(startedAt.plusSeconds(10), reservation.deadline());
        assertEquals(600d, jdbc.queryForObject(
                "select used_seconds from user_entitlements where user_id = ?", Double.class, userId));
        assertEquals("USER_QUOTA_EXHAUSTED", assertThrows(
                BusinessException.class,
                () -> policy.reserveRemaining(userId.toString(), startedAt)).code());

        policy.settleReservation(
                userId.toString(),
                reservation.quotaDate(),
                reservation.reservedSeconds(),
                reservation.startedAt(),
                startedAt.plusSeconds(4));

        assertEquals(594d, jdbc.queryForObject(
                "select used_seconds from user_entitlements where user_id = ?", Double.class, userId));
    }

    @Test
	void preservesSuspendedStatusWhenTheLedgerRollsIntoANewDay() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:user-entitlement-rollover;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("create table user_entitlements (user_id uuid, quota_date date, quota_seconds numeric(12,3), used_seconds numeric(12,3), status varchar(32), updated_at timestamp with time zone)");
        UUID userId = UUID.randomUUID();
        jdbc.update("insert into user_entitlements values (?, dateadd('DAY', -1, current_date), 600, 600, 'suspended', current_timestamp)", userId);
        var policy = new UserEntitlementPolicy(jdbc);

        assertEquals("USER_ENTITLEMENT_SUSPENDED", assertThrows(BusinessException.class,
                () -> policy.assertAllowed(userId.toString())).code());
		assertEquals(0d, jdbc.queryForObject(
				"select used_seconds from user_entitlements where user_id = ?", Double.class, userId));
	}

	@Test
	void activeJdbcGuardsMissingRowsAndNoRefundReservationAreNoOps() {
		JdbcDataSource dataSource = new JdbcDataSource();
		dataSource.setURL("jdbc:h2:mem:user-entitlement-guards;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		jdbc.execute("create table user_entitlements (user_id uuid, quota_date date, quota_seconds numeric(12,6), used_seconds numeric(12,6), status varchar(32), updated_at timestamp with time zone)");
		var policy = new UserEntitlementPolicy(jdbc);
		String missing = UUID.randomUUID().toString();
		policy.assertAllowed(missing);
		assertNull(policy.reserveRemaining(missing, Instant.now()));

		Instant start = Instant.parse("2026-01-01T00:00:00Z");
		policy.settleReservation(missing, null, 1, start, start);
		policy.settleReservation(missing, LocalDate.now(), 0, start, start);
		policy.settleReservation(missing, LocalDate.now(), 1, null, start);
		policy.settleReservation(missing, LocalDate.now(), 1, start, null);
		policy.settleReservation(missing, LocalDate.now(), 1, start, start.plusSeconds(2));
		policy.recordUsage(missing, null, start);
		policy.recordUsage(missing, start, null);
		policy.recordUsage(missing, start, start.minusSeconds(1));
	}

	@Test
	void reservesFractionalQuotaForAtLeastOneMillisecondAndSettlesNegativeDuration() {
		JdbcDataSource dataSource = new JdbcDataSource();
		dataSource.setURL("jdbc:h2:mem:user-entitlement-fraction;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		jdbc.execute("create table user_entitlements (user_id uuid, quota_date date, quota_seconds numeric(12,6), used_seconds numeric(12,6), status varchar(32), updated_at timestamp with time zone)");
		UUID userId = UUID.randomUUID();
		jdbc.update("insert into user_entitlements values (?, current_date, 1, 0.9995, 'active', current_timestamp)", userId);
		var policy = new UserEntitlementPolicy(jdbc);
		Instant start = Instant.parse("2026-01-01T00:00:00Z");

		var reservation = policy.reserveRemaining(userId.toString(), start);
		assertEquals(start.plusMillis(1), reservation.deadline());
		policy.settleReservation(userId.toString(), reservation.quotaDate(),
				reservation.reservedSeconds(), start, start.minusSeconds(1));
		assertThrows(BusinessException.class,
				() -> policy.recordUsage(null, start, start.plusSeconds(1)));
	}
}
