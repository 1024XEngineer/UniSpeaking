package com.unispeaking.component.policy;

import com.unispeaking.common.exception.BusinessException;
import java.util.UUID;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Enforces the administrator-managed account status and daily time quota. */
@Component
public class UserEntitlementPolicy {
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
                    "select status, quota_date, quota_seconds, used_seconds from user_entitlements "
                            + "where user_id = ?",
                    (rs, row) -> new Entitlement(
                            rs.getString("status"),
                            rs.getObject("quota_date", LocalDate.class),
                            rs.getDouble("quota_seconds"),
                            rs.getDouble("used_seconds")),
                    userId);
            if (entitlement == null) return;
            assertUsable(entitlement);
        } catch (EmptyResultDataAccessException ignored) {
            // Legacy accounts without a governance row retain the product default.
        }
    }

    @Transactional
    public QuotaReservation reserveRemaining(String rawUserId, Instant startedAt) {
        if (jdbc == null || startedAt == null) return null;
        UUID userId = requireUserId(rawUserId);
        rolloverEntitlement(userId);
        try {
            Entitlement entitlement = jdbc.queryForObject(
                    "select status, quota_date, quota_seconds, used_seconds from user_entitlements "
                            + "where user_id = ? for update",
                    (rs, row) -> new Entitlement(
                            rs.getString("status"),
                            rs.getObject("quota_date", LocalDate.class),
                            rs.getDouble("quota_seconds"),
                            rs.getDouble("used_seconds")),
                    userId);
            if (entitlement == null) return null;
            assertUsable(entitlement);
            double remainingSeconds = Math.max(
                    0,
                    entitlement.quotaSeconds() - entitlement.usedSeconds());
            if (remainingSeconds <= 0) {
                throw quotaExhausted();
            }
            jdbc.update(
                    "update user_entitlements set used_seconds = quota_seconds, "
                            + "updated_at = current_timestamp where user_id = ?",
                    userId);
            long reservedMillis = Math.max(
                    1,
                    (long) Math.floor(remainingSeconds * 1000d));
            return new QuotaReservation(
                    entitlement.quotaDate(),
                    reservedMillis / 1000d,
                    startedAt,
                    startedAt.plusMillis(reservedMillis));
        } catch (EmptyResultDataAccessException ignored) {
            // Legacy accounts without a governance row retain the product default.
            return null;
        }
    }

    public void settleReservation(
            String rawUserId,
            LocalDate quotaDate,
            double reservedSeconds,
            Instant startedAt,
            Instant endedAt) {
        if (jdbc == null || quotaDate == null || reservedSeconds <= 0
                || startedAt == null || endedAt == null) return;
        UUID userId = requireUserId(rawUserId);
        double usedSeconds = Math.min(
                reservedSeconds,
                Math.max(0, Duration.between(startedAt, endedAt).toMillis() / 1000d));
        double refundSeconds = Math.max(0, reservedSeconds - usedSeconds);
        if (refundSeconds <= 0) return;
        jdbc.update(
                "update user_entitlements set used_seconds = "
                        + "case when used_seconds >= ? then used_seconds - ? else 0 end, "
                        + "updated_at = current_timestamp where user_id = ? and quota_date = ?",
                refundSeconds, refundSeconds, userId, quotaDate);
    }

    private void rolloverEntitlement(UUID userId) {
        jdbc.update("update user_entitlements set used_seconds = 0, "
                        + "quota_date = current_date, updated_at = current_timestamp "
                        + "where user_id = ? and (quota_date is null or quota_date <> current_date)",
                userId);
    }

    public void recordUsage(String rawUserId, Instant startedAt, Instant endedAt) {
        if (jdbc == null || startedAt == null || endedAt == null || endedAt.isBefore(startedAt)) return;
        UUID userId = requireUserId(rawUserId);
        double seconds = Math.max(0, Duration.between(startedAt, endedAt).toMillis() / 1000d);
        jdbc.update("update user_entitlements set "
                        + "used_seconds = case when quota_date = current_date "
                        + "then least(quota_seconds, used_seconds + ?) "
                        + "else least(quota_seconds, ?) end, "
                        + "quota_date = current_date, updated_at = current_timestamp where user_id = ?",
                seconds, seconds, userId);
    }

    private UUID requireUserId(String rawUserId) {
        try {
            return UUID.fromString(rawUserId);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException("INVALID_USER_ID", "用户标识必须是 UUID");
        }
    }

    private void assertUsable(Entitlement entitlement) {
        if ("suspended".equalsIgnoreCase(entitlement.status())) {
            throw new BusinessException("USER_ENTITLEMENT_SUSPENDED", "当前账号已暂停练习权限");
        }
        if (entitlement.usedSeconds() >= entitlement.quotaSeconds()) {
            throw quotaExhausted();
        }
    }

    private BusinessException quotaExhausted() {
        return new BusinessException("USER_QUOTA_EXHAUSTED", "今日练习额度已用完");
    }

    private record Entitlement(
            String status,
            LocalDate quotaDate,
            double quotaSeconds,
            double usedSeconds) {
    }

    public record QuotaReservation(
            LocalDate quotaDate,
            double reservedSeconds,
            Instant startedAt,
            Instant deadline) {
    }
}
