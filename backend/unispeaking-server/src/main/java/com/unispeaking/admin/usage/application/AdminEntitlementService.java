package com.unispeaking.admin.usage.application;

import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** Writes the current day's user entitlement while preserving already-used time. */
@Service
public final class AdminEntitlementService {
    private static final double MAX_QUOTA_SECONDS = 86_400;
    private final JdbcTemplate jdbc;

    public AdminEntitlementService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public EntitlementView update(String rawUserId, UpdateRequest request) {
        UUID userId;
        try {
            userId = UUID.fromString(rawUserId);
        } catch (IllegalArgumentException exception) {
            throw new UsageUserNotFoundException(rawUserId);
        }
        validate(request);
        if (!userExists(userId)) {
            throw new UsageUserNotFoundException(rawUserId);
        }

        int updated = jdbc.update(
                "update user_entitlements set plan_code = ?, plan_name = ?, "
                        + "quota_date = current_date, quota_seconds = ?, "
                        + "used_seconds = case when quota_date = current_date then used_seconds else 0 end, "
                        + "status = ?, updated_at = current_timestamp where user_id = ?",
                normalize(request.planCode()), normalize(request.planName()), request.quotaSeconds(),
                normalizeStatus(request.status()), userId);
        if (updated == 0) {
            jdbc.update(
                    "insert into user_entitlements (user_id, plan_code, plan_name, quota_date, quota_seconds, used_seconds, status, updated_at) "
                            + "values (?, ?, ?, current_date, ?, 0, ?, current_timestamp)",
                    userId, normalize(request.planCode()), normalize(request.planName()), request.quotaSeconds(),
                    normalizeStatus(request.status()));
        }
        return find(userId);
    }

    private boolean userExists(UUID userId) {
        try {
            return jdbc.queryForObject("select count(*) from \"user\" where id = ?", Integer.class, userId) > 0;
        } catch (org.springframework.dao.DataAccessException exception) {
            // Keeps the in-memory/H2 governance tests compatible with pre-V10 schemas.
            return jdbc.queryForObject("select count(*) from app_users where id = ?", Integer.class, userId) > 0;
        }
    }

    private EntitlementView find(UUID userId) {
        return jdbc.queryForObject(
                "select user_id, plan_code, plan_name, quota_date, quota_seconds, used_seconds, status "
                        + "from user_entitlements where user_id = ?",
                (rs, row) -> new EntitlementView(
                        rs.getObject("user_id", UUID.class).toString(), rs.getString("plan_code"),
                        rs.getString("plan_name"), rs.getObject("quota_date").toString(),
                        rs.getDouble("quota_seconds"), rs.getDouble("used_seconds"), rs.getString("status")),
                userId);
    }

    private static void validate(UpdateRequest request) {
        if (request == null || !StringUtils.hasText(request.planCode()) || !StringUtils.hasText(request.planName())) {
            throw new InvalidEntitlementException("套餐编码和套餐名称不能为空");
        }
        if (!Double.isFinite(request.quotaSeconds()) || request.quotaSeconds() < 0 || request.quotaSeconds() > MAX_QUOTA_SECONDS) {
            throw new InvalidEntitlementException("每日额度必须在 0 到 86400 秒之间");
        }
        normalizeStatus(request.status());
    }

    private static String normalize(String value) {
        return value.trim();
    }

    private static String normalizeStatus(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "active";
        if (!normalized.equals("active") && !normalized.equals("suspended")) {
            throw new InvalidEntitlementException("权限状态只能是 active 或 suspended");
        }
        return normalized;
    }

    public record UpdateRequest(String planCode, String planName, double quotaSeconds, String status) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record EntitlementView(
            String userId,
            String planCode,
            String planName,
            String quotaDate,
            double quotaSeconds,
            double usedSeconds,
            String status) {}

    public static final class InvalidEntitlementException extends RuntimeException {
        public InvalidEntitlementException(String message) {
            super(message);
        }
    }
}
