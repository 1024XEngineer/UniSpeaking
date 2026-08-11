package com.unispeaking.admin.usage.application;

import static org.assertj.core.api.Assertions.assertThat;

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
        jdbc.execute("create table \"user\" (id uuid primary key, username varchar(320) not null)");
        jdbc.execute("create table user_entitlements (user_id uuid primary key, quota_date date not null, "
                + "plan_code varchar(64) not null, plan_name varchar(128) not null, quota_seconds numeric(12,3) not null, "
                + "used_seconds numeric(12,3) not null, status varchar(32) not null, updated_at timestamp with time zone not null)");
        UUID userId = UUID.fromString("22222222-2222-4222-8222-222222222222");
        jdbc.update("insert into \"user\" (id, username) values (?, ?)", userId, "learner@example.com");
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
}
