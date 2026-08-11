package com.unispeaking.component.policy;

import com.unispeaking.common.exception.BusinessException;
import java.util.UUID;
import java.time.Duration;
import java.time.Instant;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Enforces the administrator-managed account status and daily time quota. */
@Component
public final class UserEntitlementPolicy {
    private final JdbcTemplate jdbc;

    public UserEntitlementPolicy(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void assertAllowed(String rawUserId) {
        if (jdbc == null) return;
        UUID userId;
        try {
            userId = UUID.fromString(rawUserId);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("INVALID_USER_ID", "用户标识必须是 UUID");
        }
        rolloverEntitlement(userId);
        try {
            Entitlement entitlement = jdbc.queryForObject(
                    "select status, quota_seconds, used_seconds from user_entitlements "
                            + "where user_id = ?",
                    (rs, row) -> new Entitlement(
                            rs.getString("status"), rs.getDouble("quota_seconds"), rs.getDouble("used_seconds")),
                    userId);
            if (entitlement == null) return;
            if ("suspended".equalsIgnoreCase(entitlement.status())) {
                throw new BusinessException("USER_ENTITLEMENT_SUSPENDED", "当前账号已暂停练习权限");
            }
            if (entitlement.usedSeconds() >= entitlement.quotaSeconds()) {
                throw new BusinessException("USER_QUOTA_EXHAUSTED", "今日练习额度已用完");
            }
        } catch (EmptyResultDataAccessException ignored) {
            // Legacy accounts without a governance row retain the product default.
        }
    }

    private void rolloverEntitlement(UUID userId) {
        jdbc.update("update user_entitlements set used_seconds = 0, "
                        + "quota_date = current_date, updated_at = current_timestamp "
                        + "where user_id = ? and (quota_date is null or quota_date <> current_date)",
                userId);
    }

    public void recordUsage(String rawUserId, Instant startedAt, Instant endedAt) {
        if (jdbc == null || startedAt == null || endedAt == null || endedAt.isBefore(startedAt)) return;
        UUID userId;
        try {
            userId = UUID.fromString(rawUserId);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("INVALID_USER_ID", "用户标识必须是 UUID");
        }
        double seconds = Math.max(0, Duration.between(startedAt, endedAt).toMillis() / 1000d);
        jdbc.update("update user_entitlements set "
                        + "used_seconds = case when quota_date = current_date then used_seconds + ? else ? end, "
                        + "quota_date = current_date, updated_at = current_timestamp where user_id = ?",
                seconds, seconds, userId);
    }

    private record Entitlement(String status, double quotaSeconds, double usedSeconds) {
    }
}
