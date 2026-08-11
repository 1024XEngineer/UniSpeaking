package com.unispeaking.component.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.unispeaking.common.exception.BusinessException;
import java.util.UUID;
import java.time.Instant;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class UserEntitlementPolicyTest {
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
}
