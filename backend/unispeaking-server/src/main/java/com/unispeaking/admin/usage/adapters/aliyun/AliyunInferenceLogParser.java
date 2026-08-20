package com.unispeaking.admin.usage.adapters.aliyun;

import com.unispeaking.admin.usage.domain.ModelUsage;
import com.unispeaking.admin.usage.domain.OfficialUsageRecord;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Locale;
import java.util.regex.Pattern;

@Component
public final class AliyunInferenceLogParser {
    private static final Pattern STABLE_ID = Pattern.compile("[A-Za-z0-9_.:-]{1,128}");
    private final ObjectMapper mapper;

    public AliyunInferenceLogParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public OfficialUsageRecord parse(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            String statusCode = requiredText(root, "status_code");
            JsonNode usage = "200".equals(statusCode)
                    ? objectNode(root.path("usage"), "usage")
                    : mapper.createObjectNode();
            long characters = optionalLong(usage, "characters");
            var officialUsage = characters > 0
                    ? new ModelUsage(1, 0, 0, 0, 0, 0, 0, 0)
                    : tokenUsage(usage, statusCode);
            var record = new OfficialUsageRecord(
                    requiredText(root, "request_id"),
                    optionalText(root, "task_uuid"),
                    requiredLong(root, "start_unix_timestamp"),
                    requiredLong(root, "duration"),
                    statusCode,
                    requiredText(root, "model"),
                    requiredText(root, "workspace_id"),
                    requiredText(root, "apikey_id"),
                    requiredText(objectNode(root.path("extras"), "extras"), "protocol")
                            .toLowerCase(Locale.ROOT),
                    officialUsage,
                    characters);
            validate(record);
            return record;
        } catch (OfficialUsageSchemaException exception) {
            throw exception;
        } catch (JacksonException | NumberFormatException exception) {
            throw new OfficialUsageSchemaException("阿里云推理日志格式无效", exception);
        }
    }

    private static ModelUsage tokenUsage(JsonNode usage, String statusCode) {
        if (!"200".equals(statusCode)) {
            return new ModelUsage(0, 0, 0, 0, 0, 0, 0, 0);
        }
        JsonNode inputDetails = usage.path("input_tokens_details");
        JsonNode outputDetails = usage.path("output_tokens_details");
        return new ModelUsage(
                1,
                requiredLong(usage, "total_tokens"),
                requiredLong(usage, "input_tokens"),
                requiredLong(usage, "output_tokens"),
                optionalLong(inputDetails, "text_tokens"),
                optionalLong(inputDetails, "audio_tokens"),
                optionalLong(outputDetails, "text_tokens"),
                optionalLong(outputDetails, "audio_tokens"));
    }

    private static void validate(OfficialUsageRecord record) {
        requireStableId("request_id", record.requestId());
        if (record.taskUuid() != null) {
            requireStableId("task_uuid", record.taskUuid());
        }
        if (record.startedAtEpochMs() < 0) {
            throw new OfficialUsageSchemaException("start_unix_timestamp 不能为负数");
        }
        if (record.durationMs() < 0) {
            throw new OfficialUsageSchemaException("duration 不能为负数");
        }
        if (!"ws".equals(record.protocol()) && !"webrtc".equals(record.protocol())
                && !"http".equals(record.protocol())) {
            throw new OfficialUsageSchemaException("protocol 必须为 ws、webrtc 或 http");
        }
        if (("ws".equals(record.protocol()) || "webrtc".equals(record.protocol()))
                && record.taskUuid() == null) {
            throw new OfficialUsageSchemaException("task_uuid 缺失");
        }
        if (!"200".equals(record.statusCode())) {
            return;
        }
        var usage = record.usage();
        if (usage.inputTokens() != usage.inputTextTokens() + usage.inputAudioTokens()) {
            throw new OfficialUsageSchemaException("input_tokens 与文本/音频明细不一致");
        }
        if (usage.outputTokens() != usage.outputTextTokens() + usage.outputAudioTokens()) {
            throw new OfficialUsageSchemaException("output_tokens 与文本/音频明细不一致");
        }
        if (usage.totalTokens() != usage.inputTokens() + usage.outputTokens()) {
            throw new OfficialUsageSchemaException("total_tokens 与输入/输出合计不一致");
        }
    }

    private static void requireStableId(String field, String value) {
        if (!STABLE_ID.matcher(value).matches()) {
            throw new OfficialUsageSchemaException(field + " 缺失或格式无效");
        }
    }

    private static String requiredText(JsonNode parent, String field) {
        JsonNode node = parent.path(field);
        String value = node.asText();
        if (value == null || value.isBlank()) {
            throw new OfficialUsageSchemaException(field + " 缺失");
        }
        return value;
    }

    private static String optionalText(JsonNode parent, String field) {
        JsonNode node = parent.path(field);
        String value = node.asText();
        return value == null || value.isBlank() ? null : value;
    }

    private static long requiredLong(JsonNode parent, String field) {
        JsonNode node = parent.path(field);
        String value = node.asText();
        if (value == null || value.isBlank()) {
            throw new OfficialUsageSchemaException(field + " 缺失");
        }
        try {
            long parsed = Long.parseLong(value);
            if (parsed < 0) {
                throw new OfficialUsageSchemaException(field + " 不能为负数");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new OfficialUsageSchemaException(field + " 不是有效整数", exception);
        }
    }

    private static long optionalLong(JsonNode parent, String field) {
        JsonNode node = parent.path(field);
        if (node.isMissingNode() || node.isNull() || node.asText().isBlank()) {
            return 0;
        }
        return requiredLong(parent, field);
    }

    private JsonNode objectNode(JsonNode node, String field) {
        try {
            JsonNode resolved = node.isTextual() ? mapper.readTree(node.asText()) : node;
            if (!resolved.isObject()) {
                throw new OfficialUsageSchemaException(field + " 必须为 JSON 对象");
            }
            return resolved;
        } catch (JacksonException exception) {
            throw new OfficialUsageSchemaException(field + " 不是有效 JSON 对象", exception);
        }
    }
}
