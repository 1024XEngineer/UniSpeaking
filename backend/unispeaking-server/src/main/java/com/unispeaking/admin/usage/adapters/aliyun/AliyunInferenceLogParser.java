package com.unispeaking.admin.usage.adapters.aliyun;

import com.unispeaking.admin.usage.domain.ModelUsage;
import com.unispeaking.admin.usage.domain.OfficialUsageRecord;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

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
            JsonNode usage = objectNode(root.path("usage"), "usage");
            JsonNode inputDetails = usage.path("input_tokens_details");
            JsonNode outputDetails = usage.path("output_tokens_details");
            var officialUsage = new ModelUsage(
                    1,
                    requiredLong(usage, "total_tokens"),
                    requiredLong(usage, "input_tokens"),
                    requiredLong(usage, "output_tokens"),
                    requiredLong(inputDetails, "text_tokens"),
                    requiredLong(inputDetails, "audio_tokens"),
                    requiredLong(outputDetails, "text_tokens"),
                    requiredLong(outputDetails, "audio_tokens"));
            var record = new OfficialUsageRecord(
                    requiredText(root, "request_id"),
                    requiredText(root, "task_uuid"),
                    requiredLong(root, "start_unix_timestamp"),
                    requiredLong(root, "duration"),
                    requiredText(root, "status_code"),
                    requiredText(root, "model"),
                    requiredText(root, "workspace_id"),
                    requiredText(root, "apikey_id"),
                    requiredText(objectNode(root.path("extras"), "extras"), "protocol"),
                    officialUsage);
            validate(record);
            return record;
        } catch (OfficialUsageSchemaException exception) {
            throw exception;
        } catch (JacksonException | NumberFormatException exception) {
            throw new OfficialUsageSchemaException("阿里云推理日志格式无效", exception);
        }
    }

    private static void validate(OfficialUsageRecord record) {
        requireStableId("request_id", record.requestId());
        requireStableId("task_uuid", record.taskUuid());
        if (record.startedAtEpochMs() < 0) {
            throw new OfficialUsageSchemaException("start_unix_timestamp 不能为负数");
        }
        if (record.durationMs() < 0) {
            throw new OfficialUsageSchemaException("duration 不能为负数");
        }
        if (!"ws".equals(record.protocol()) && !"webrtc".equals(record.protocol())) {
            throw new OfficialUsageSchemaException("protocol 必须为 ws 或 webrtc");
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
