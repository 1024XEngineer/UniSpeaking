package com.unispeaking.admin.usage.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AdminEntitlementServiceTest {
    @Test
	void updatesAnExistingUsersCurrentEntitlementWithoutChangingUsage() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:admin-entitlement;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("create table users (id uuid primary key, username varchar(320) not null)");
        jdbc.execute("create table user_entitlements (user_id uuid primary key, quota_date date not null, "
                + "plan_code varchar(64) not null, plan_name varchar(128) not null, quota_seconds numeric(12,3) not null, "
                + "used_seconds numeric(12,3) not null, status varchar(32) not null, updated_at timestamp with time zone not null)");
        UUID userId = UUID.fromString("22222222-2222-4222-8222-222222222222");
        jdbc.update("insert into users (id, username) values (?, ?)", userId, "learner@example.com");
        jdbc.update("insert into user_entitlements (user_id, quota_date, plan_code, plan_name, quota_seconds, used_seconds, status, updated_at) "
                        + "values (?, current_date, 'free', 'Free', 600, 125.5, 'active', current_timestamp)", userId);

        var updated = new AdminEntitlementService(jdbc).update(userId.toString(),
                new AdminEntitlementService.UpdateRequest("pro", "Pro", 3600, "active"));

        assertThat(updated.userId()).isEqualTo(userId.toString());
        assertThat(updated.planCode()).isEqualTo("pro");
        assertThat(updated.planName()).isEqualTo("Pro");
        assertThat(updated.quotaSeconds()).isEqualTo(3600);
        assertThat(updated.usedSeconds()).isEqualTo(125.5);
        assertThat(updated.status()).isEqualTo("active");
	}

	@Test
	void insertsMissingEntitlementDefaultsStatusAndTrimsNames() {
		JdbcTemplate jdbc = database("admin-entitlement-insert");
		UUID userId = UUID.randomUUID();
		jdbc.update("insert into users (id, username) values (?, ?)", userId, "learner@example.com");

		var updated = new AdminEntitlementService(jdbc).update(userId.toString(),
				new AdminEntitlementService.UpdateRequest(" pro ", " Pro ", 0, null));

		assertThat(updated.planCode()).isEqualTo("pro");
		assertThat(updated.planName()).isEqualTo("Pro");
		assertThat(updated.status()).isEqualTo("active");
		assertThat(updated.usedSeconds()).isZero();
	}

	@Test
	void resetsPriorDateUsageAndNormalizesSuspendedStatus() {
		JdbcTemplate jdbc = database("admin-entitlement-rollover");
		UUID userId = UUID.randomUUID();
		jdbc.update("insert into users (id, username) values (?, ?)", userId, "learner@example.com");
		jdbc.update("insert into user_entitlements values (?, dateadd('DAY', -1, current_date), 'free', 'Free', 600, 500, 'active', current_timestamp)", userId);

		var updated = new AdminEntitlementService(jdbc).update(userId.toString(),
				new AdminEntitlementService.UpdateRequest("pro", "Pro", 86400, " SUSPENDED "));

		assertThat(updated.usedSeconds()).isZero();
		assertThat(updated.status()).isEqualTo("suspended");
	}

	@Test
	void rejectsUnknownUsersMalformedIdsAndEveryInvalidRequestBoundary() {
		JdbcTemplate jdbc = database("admin-entitlement-invalid");
		AdminEntitlementService service = new AdminEntitlementService(jdbc);
		assertThrows(UsageUserNotFoundException.class,
				() -> service.update("not-uuid", new AdminEntitlementService.UpdateRequest("p", "P", 1, "active")));
		assertThrows(UsageUserNotFoundException.class,
				() -> service.update(UUID.randomUUID().toString(), new AdminEntitlementService.UpdateRequest("p", "P", 1, "active")));
		assertThrows(AdminEntitlementService.InvalidEntitlementException.class,
				() -> service.update(UUID.randomUUID().toString(), null));
		for (AdminEntitlementService.UpdateRequest request : new AdminEntitlementService.UpdateRequest[] {
				new AdminEntitlementService.UpdateRequest(" ", "P", 1, "active"),
				new AdminEntitlementService.UpdateRequest("p", " ", 1, "active"),
				new AdminEntitlementService.UpdateRequest("p", "P", Double.NaN, "active"),
				new AdminEntitlementService.UpdateRequest("p", "P", -1, "active"),
				new AdminEntitlementService.UpdateRequest("p", "P", 86401, "active"),
				new AdminEntitlementService.UpdateRequest("p", "P", 1, "blocked")
		}) {
			assertThrows(AdminEntitlementService.InvalidEntitlementException.class,
					() -> service.update(UUID.randomUUID().toString(), request));
		}
	}

	private JdbcTemplate database(String name) {
		JdbcDataSource dataSource = new JdbcDataSource();
		dataSource.setURL("jdbc:h2:mem:" + name + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		jdbc.execute("create table users (id uuid primary key, username varchar(320) not null)");
		jdbc.execute("create table user_entitlements (user_id uuid primary key, quota_date date not null, "
				+ "plan_code varchar(64) not null, plan_name varchar(128) not null, quota_seconds numeric(12,3) not null, "
				+ "used_seconds numeric(12,3) not null, status varchar(32) not null, updated_at timestamp with time zone not null)");
		return jdbc;
	}
}
