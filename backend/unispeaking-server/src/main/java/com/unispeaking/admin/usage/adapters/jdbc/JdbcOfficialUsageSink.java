package com.unispeaking.admin.usage.adapters.jdbc;

import com.unispeaking.admin.usage.domain.ModelUsage;
import com.unispeaking.admin.usage.domain.OfficialUsageRecord;
import com.unispeaking.admin.usage.ports.OfficialUsageSink;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Stores official Alibaba inference usage in the canonical backend database. */
@Component
@ConditionalOnProperty(
        name = "unispeaking.integrations.freetalk.enabled",
        havingValue = "false",
        matchIfMissing = true)
public final class JdbcOfficialUsageSink implements OfficialUsageSink {
    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    @Autowired
    public JdbcOfficialUsageSink(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    /** Test and standalone adapter constructor. */
    public JdbcOfficialUsageSink(JdbcTemplate jdbc) {
        this(jdbc, new ObjectMapper());
    }

    @Override
    public synchronized ImportResult importRecords(List<OfficialUsageRecord> records) {
        int imported = 0;
        int duplicates = 0;
        int matched = 0;
        int unmatched = 0;
        for (OfficialUsageRecord record : records) {
            if (exists(record.requestId())) {
                refreshPreviouslyEmptyUsage(record);
                duplicates++;
            } else {
                try {
                    insert(record);
                    imported++;
                } catch (DuplicateKeyException exception) {
                    duplicates++;
                }
            }
        }
        for (OfficialUsageRecord record : records) {
            int reconciled = reconcileInvocation(record);
            if (reconciled > 0 || isBound(record.taskUuid())) matched++;
            else unmatched++;
        }
        return new ImportResult(imported, duplicates, matched, unmatched);
    }

    /**
     * Older sync runs stored non-200 Realtime rows with zero token fields.
     * Keep imports idempotent while allowing a later, richer SLS payload to
     * repair those rows in place.
     */
    private void refreshPreviouslyEmptyUsage(OfficialUsageRecord record) {
        var usage = record.usage();
        if (usage.totalTokens() <= 0 && record.characters() <= 0) {
            return;
        }
        jdbc.update(
                "update official_usage_records set status_code = ?, model = ?, protocol = ?, "
                        + "total_tokens = ?, input_tokens = ?, output_tokens = ?, input_text_tokens = ?, "
                        + "input_audio_tokens = ?, output_text_tokens = ?, output_audio_tokens = ?, characters = ? "
                        + "where request_id = ? and total_tokens = 0 and characters = 0",
                record.statusCode(), record.model(), record.protocol(), usage.totalTokens(), usage.inputTokens(),
                usage.outputTokens(), usage.inputTextTokens(), usage.inputAudioTokens(), usage.outputTextTokens(),
                usage.outputAudioTokens(), record.characters(), record.requestId());
    }

    private void insert(OfficialUsageRecord record) {
        var usage = record.usage();
        jdbc.update(
                "insert into official_usage_records "
                        + "(request_id, task_uuid, started_at_epoch_ms, duration_ms, status_code, model, "
                        + "workspace_id, apikey_id, protocol, requests, total_tokens, input_tokens, "
                        + "output_tokens, input_text_tokens, input_audio_tokens, output_text_tokens, "
                        + "output_audio_tokens, characters, imported_at) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                record.requestId(), record.taskUuid(), record.startedAtEpochMs(), record.durationMs(),
                record.statusCode(), record.model(), record.workspaceId(), record.apiKeyId(), record.protocol(),
                usage.responseCount(), usage.totalTokens(), usage.inputTokens(), usage.outputTokens(),
                usage.inputTextTokens(), usage.inputAudioTokens(), usage.outputTextTokens(),
                usage.outputAudioTokens(), record.characters(), OffsetDateTime.now(ZoneOffset.UTC));
    }

    private int reconcileInvocation(OfficialUsageRecord record) {
        int direct = updateInvocation(
                "provider_request_id = ? and status in ('SUCCEEDED', 'FAILED') "
                        + "and business_scene <> 'realtime_session'",
                record,
                record.requestId());
        if (direct > 0 || record.taskUuid() == null) {
            return direct;
        }
        int realtime = 0;
        List<String> sessionIds = jdbc.queryForList(
                "select session_id from practice_session where provider_session_id = ?",
                String.class,
                record.taskUuid());
        OfficialUsageRecord aggregate = aggregateRealtimeRecord(record.taskUuid());
        for (String sessionId : sessionIds) {
            realtime += updateInvocation(
                    "session_id = ? and business_scene = 'realtime_session' and status = 'SUCCEEDED'",
                    aggregate,
                    sessionId);
        }
        return realtime;
    }

    private OfficialUsageRecord aggregateRealtimeRecord(String taskUuid) {
        return jdbc.queryForObject(
                "select max(request_id) request_id, min(started_at_epoch_ms) started_at_epoch_ms, "
                        + "coalesce(sum(duration_ms), 0) duration_ms, max(status_code) status_code, "
                        + "max(model) model, max(workspace_id) workspace_id, max(apikey_id) apikey_id, "
                        + "max(protocol) protocol, coalesce(sum(requests), 0) requests, "
                        + "coalesce(sum(total_tokens), 0) total_tokens, "
                        + "coalesce(sum(input_tokens), 0) input_tokens, "
                        + "coalesce(sum(output_tokens), 0) output_tokens, "
                        + "coalesce(sum(input_text_tokens), 0) input_text_tokens, "
                        + "coalesce(sum(input_audio_tokens), 0) input_audio_tokens, "
                        + "coalesce(sum(output_text_tokens), 0) output_text_tokens, "
                        + "coalesce(sum(output_audio_tokens), 0) output_audio_tokens, "
                        + "coalesce(sum(characters), 0) characters "
                        + "from official_usage_records where task_uuid = ?",
                (rs, row) -> new OfficialUsageRecord(
                        rs.getString("request_id"), taskUuid, rs.getLong("started_at_epoch_ms"),
                        rs.getLong("duration_ms"), rs.getString("status_code"), rs.getString("model"),
                        rs.getString("workspace_id"), rs.getString("apikey_id"), rs.getString("protocol"),
                        new ModelUsage(
                                rs.getLong("requests"), rs.getLong("total_tokens"),
                                rs.getLong("input_tokens"), rs.getLong("output_tokens"),
                                rs.getLong("input_text_tokens"), rs.getLong("input_audio_tokens"),
                                rs.getLong("output_text_tokens"), rs.getLong("output_audio_tokens")),
                        rs.getLong("characters")),
                taskUuid);
    }

    private int updateInvocation(String predicate, OfficialUsageRecord record, Object binding) {
        var usage = record.usage();
        List<InvocationTarget> targets = jdbc.query(
                "select invocation_id, model_id, pricing_snapshot, usage_source, estimated_cost "
                        + "from ai_model_invocations where " + predicate,
                (rs, row) -> new InvocationTarget(
                        rs.getObject("invocation_id"),
                        rs.getString("model_id"),
                        rs.getObject("pricing_snapshot"),
                        rs.getString("usage_source"),
                        rs.getBigDecimal("estimated_cost")),
                binding);
        int updated = 0;
        for (InvocationTarget target : targets) {
            Pricing snapshotPricing = snapshotPricing(target);
            BigDecimal cost = snapshotPricing != null
                    ? estimatedCost(record, snapshotPricing)
                    : existingOfficialCost(target, record);
            updated += jdbc.update(
                    "update ai_model_invocations set provider_request_id = ?, input_tokens = ?, output_tokens = ?, "
                            + "total_tokens = ?, input_characters = ?, output_characters = 0, usage_source = 'OFFICIAL', "
                            + "estimated_cost = ? where invocation_id = ?",
                    record.requestId(), usage.inputTokens(), usage.outputTokens(), usage.totalTokens(),
                    record.characters(), cost, target.invocationId());
        }
        return updated;
    }

    private BigDecimal existingOfficialCost(InvocationTarget target, OfficialUsageRecord record) {
        if ("OFFICIAL".equalsIgnoreCase(target.usageSource())) {
            return value(target.estimatedCost()).setScale(8, RoundingMode.HALF_UP);
        }
        return estimatedCost(record, currentPricing(target.modelId()));
    }

    private Pricing snapshotPricing(InvocationTarget target) {
        try {
            JsonNode root = objectMapper.readTree(String.valueOf(target.pricingSnapshot()));
            String billingUnit = root.path("billing_unit").asText("").toUpperCase(java.util.Locale.ROOT);
            BigDecimal inputPrice = decimal(root, "input_price_per_million");
            BigDecimal outputPrice = decimal(root, "output_price_per_million");
            BigDecimal characterPrice = decimal(root, "character_price_per_million");
            BigDecimal requestPrice = decimal(root, "request_price_per_call");
            if (List.of("TOKENS", "CHARACTERS", "REQUESTS", "MIXED").contains(billingUnit)
                    && inputPrice != null && outputPrice != null
                    && characterPrice != null && requestPrice != null) {
                return new Pricing(billingUnit, inputPrice, outputPrice, characterPrice, requestPrice);
            }
        } catch (RuntimeException ignored) {
            // Legacy rows may predate pricing snapshots; use the current catalog only for those rows.
        }
        return null;
    }

    private Pricing currentPricing(String modelId) {
        List<Pricing> prices = jdbc.query(
                "select billing_unit, input_price_per_million, output_price_per_million, "
                        + "character_price_per_million, request_price_per_call from ai_models where model_id = ?",
                (rs, row) -> new Pricing(
                        rs.getString("billing_unit"),
                        rs.getBigDecimal("input_price_per_million"),
                        rs.getBigDecimal("output_price_per_million"),
                        rs.getBigDecimal("character_price_per_million"),
                        rs.getBigDecimal("request_price_per_call")),
                modelId);
        return prices.isEmpty() ? new Pricing("MIXED", null, null, null, null) : prices.getFirst();
    }

    private BigDecimal estimatedCost(OfficialUsageRecord record, Pricing price) {
        BigDecimal tokenCost = BigDecimal.valueOf(record.usage().inputTokens())
                .multiply(value(price.inputPrice())).divide(MILLION)
                .add(BigDecimal.valueOf(record.usage().outputTokens())
                        .multiply(value(price.outputPrice())).divide(MILLION));
        BigDecimal characterCost = BigDecimal.valueOf(record.characters())
                .multiply(value(price.characterPrice())).divide(MILLION);
        BigDecimal cost = switch (price.billingUnit().toUpperCase(java.util.Locale.ROOT)) {
            case "TOKENS" -> tokenCost;
            case "CHARACTERS" -> characterCost;
            case "REQUESTS" -> value(price.requestPrice());
            default -> tokenCost.add(characterCost);
        };
        return cost.setScale(8, RoundingMode.HALF_UP);
    }

    private static BigDecimal decimal(JsonNode root, String field) {
        String value = root.path(field).asText("");
        if (value.isBlank()) return null;
        try {
            BigDecimal decimal = new BigDecimal(value);
            return decimal.signum() < 0 ? null : decimal;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static BigDecimal value(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private boolean exists(String requestId) {
        Long count = jdbc.queryForObject(
                "select count(*) from official_usage_records where request_id = ?",
                Long.class,
                requestId);
        return count != null && count > 0;
    }

    private boolean isBound(String taskUuid) {
        if (taskUuid == null || taskUuid.isBlank()) return false;
        Long count = jdbc.queryForObject(
                "select count(*) from practice_session where provider_session_id = ?",
                Long.class,
                taskUuid);
        return count != null && count > 0;
    }

    private record Pricing(
            String billingUnit,
            BigDecimal inputPrice,
            BigDecimal outputPrice,
            BigDecimal characterPrice,
            BigDecimal requestPrice) {}

    private record InvocationTarget(
            Object invocationId,
            String modelId,
            Object pricingSnapshot,
            String usageSource,
            BigDecimal estimatedCost) {}
}
