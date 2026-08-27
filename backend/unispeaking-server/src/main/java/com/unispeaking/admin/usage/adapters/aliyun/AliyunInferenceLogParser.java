package com.unispeaking.admin.usage.adapters.aliyun;

import com.unispeaking.admin.usage.domain.ModelUsage;
import com.unispeaking.admin.usage.domain.OfficialUsageRecord;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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
            JsonNode envelope = unwrapSlsMessage(mapper.readTree(json));
            validateNestedObjectShape(envelope, "extras");
            validateNestedObjectShape(envelope, "usage");
            JsonNode response = objectNodeOrNull(envelope.path("response"));
            JsonNode request = objectNodeOrNull(envelope.path("request"));
            JsonNode body = objectNodeOrNull(envelope.path("body"));
            JsonNode requestBody = request == null ? null : objectNodeOrNull(request.path("body"));
            JsonNode responseBody = response == null ? null : objectNodeOrNull(response.path("body"));
            JsonNode root = merge(envelope, request, requestBody, response, responseBody, body);
            String statusCode = firstText(root, "status_code", "http_status", "status");
            if (statusCode == null) {
                throw new OfficialUsageSchemaException("status_code 缺失; 顶层字段=" + fieldNames(root)
                        + "; 嵌套字段=" + nestedShapes(root));
            }
            JsonNode usage = objectNodeOrNull(root.path("usage"));
            if (usage == null) {
                usage = mapper.createObjectNode();
            }
            long characters = optionalLong(usage, "characters");
            var officialUsage = characters > 0
                    ? new ModelUsage(1, 0, 0, 0, 0, 0, 0, 0)
                    : tokenUsage(usage, statusCode);
            var record = new OfficialUsageRecord(
                    requiredText(root, "request_id", "requestId"),
                    optionalText(root, "task_uuid", "task_id", "session_id", "conversation_id"),
                    startTimestamp(root),
                    optionalLong(root, "duration", "duration_ms", "latency", "total_duration"),
                    statusCode,
                    requiredText(root, "model", "model_name"),
                    requiredText(root, "workspace_id", "workspace"),
                    firstTextOrDefault(root, "apikey_id", "api_key_id", "uid", "sub_uid", "unknown"),
                    protocol(root),
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
        JsonNode inputDetails = usage.path("input_tokens_details");
        JsonNode outputDetails = usage.path("output_tokens_details");
        if (!"200".equals(statusCode)) {
            boolean hasTokenUsage = firstNode(usage, "total_tokens", "input_tokens", "output_tokens") != null
                    && (usage.has("total_tokens") || usage.has("input_tokens") || usage.has("output_tokens"));
            return new ModelUsage(
                    hasTokenUsage ? 1 : 0,
                    optionalLong(usage, "total_tokens"),
                    optionalLong(usage, "input_tokens"),
                    optionalLong(usage, "output_tokens"),
                    optionalLong(inputDetails, "text_tokens"),
                    optionalLong(inputDetails, "audio_tokens"),
                    optionalLong(outputDetails, "text_tokens"),
                    optionalLong(outputDetails, "audio_tokens"));
        }
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

    private static String requiredText(JsonNode parent, String... fields) {
        JsonNode node = firstNode(parent, fields);
        String value = node.asText();
        if (value == null || value.isBlank()) {
            throw new OfficialUsageSchemaException(fields[0] + " 缺失");
        }
        return value;
    }

    private static String optionalText(JsonNode parent, String... fields) {
        JsonNode node = firstNode(parent, fields);
        String value = node.asText();
        return value.isBlank() ? null : value;
    }

    private static long requiredLong(JsonNode parent, String... fields) {
        JsonNode node = firstNode(parent, fields);
        String value = node.asText();
        if (value.isBlank()) {
            throw new OfficialUsageSchemaException(fields[0] + " 缺失");
        }
        try {
            long parsed = Long.parseLong(value);
            if (parsed < 0) {
                throw new OfficialUsageSchemaException(fields[0] + " 不能为负数");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new OfficialUsageSchemaException(fields[0] + " 不是有效整数", exception);
        }
    }

    private static long optionalLong(JsonNode parent, String... fields) {
        JsonNode node = firstNode(parent, fields);
        if (node == null || node.isMissingNode() || node.isNull() || node.asText().isBlank()) {
            return 0;
        }
        return requiredLong(parent, fields);
    }

    private long startTimestamp(JsonNode root) {
        JsonNode timestamp = firstNode(root, "start_unix_timestamp", "started_at_epoch_ms", "start_time", "logtime");
        if (timestamp.isMissingNode() || timestamp.isNull() || timestamp.asText().isBlank()) {
            throw new OfficialUsageSchemaException("start_unix_timestamp 缺失");
        }
        String value = timestamp.asText().trim();
        try {
            if (value.matches("\\d+")) {
                long parsed = Long.parseLong(value);
                return parsed < 100_000_000_000L ? parsed * 1000 : parsed;
            }
            try {
                return Instant.parse(value).toEpochMilli();
            } catch (DateTimeParseException ignored) {
                return LocalDateTime.parse(value.replace(' ', 'T'), DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        .toInstant(ZoneOffset.UTC).toEpochMilli();
            }
        } catch (RuntimeException exception) {
            throw new OfficialUsageSchemaException("start_unix_timestamp 不是有效时间", exception);
        }
    }

    private String protocol(JsonNode root) {
        JsonNode extras = objectNodeOrNull(root.path("extras"));
        String value = firstText(extras, "protocol", "transport", "protocol_type");
        if (value == null) {
            value = firstText(root, "protocol", "transport", "protocol_type", "connection_type");
        }
        if (value == null) {
            throw new OfficialUsageSchemaException("protocol 缺失");
        }
        return normalizeProtocol(value);
    }

    private static String normalizeProtocol(String value) {
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "ws", "websocket", "web_socket" -> "ws";
            case "webrtc", "web_rtc", "web-rtc" -> "webrtc";
            case "http", "https" -> "http";
            default -> value.trim().toLowerCase(Locale.ROOT);
        };
    }

    private String firstTextOrDefault(JsonNode parent, String first, String second, String third, String fourth,
            String fallback) {
        String value = firstText(parent, first, second, third, fourth);
        return value == null ? fallback : value;
    }

    private static String firstText(JsonNode parent, String... fields) {
        if (parent == null || parent.isMissingNode() || parent.isNull()) {
            return null;
        }
        for (String field : fields) {
            String value = optionalText(parent, field);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static JsonNode firstNode(JsonNode parent, String... fields) {
        if (parent == null || parent.isMissingNode() || parent.isNull()) {
            return null;
        }
        for (String field : fields) {
            JsonNode node = parent.path(field);
            if (!node.isMissingNode() && !node.isNull()) {
                return node;
            }
        }
        return parent.path(fields[0]);
    }

    private JsonNode objectNodeOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        try {
            JsonNode resolved = node;
            for (int depth = 0; depth < 3 && resolved.isTextual(); depth++) {
                resolved = mapper.readTree(resolved.asText());
            }
            return resolved != null && resolved.isObject() ? resolved : null;
        } catch (JacksonException exception) {
            return null;
        }
    }

    private String nestedShapes(JsonNode root) {
        var shapes = new java.util.ArrayList<String>();
        for (String field : new String[] {"request", "response", "body", "messages", "parameters", "choices", "usage"}) {
            JsonNode node = root.path(field);
            if (node.isMissingNode() || node.isNull()) {
                continue;
            }
            StringBuilder shape = new StringBuilder(field).append(":").append(node.isTextual() ? "text" : node.getNodeType());
            if (node.isTextual()) {
                String text = node.asText();
                shape.append("(").append(text.length()).append(")");
                try {
                    JsonNode decoded = node;
                    for (int depth = 0; depth < 3 && decoded.isTextual(); depth++) {
                        decoded = mapper.readTree(decoded.asText());
                    }
                    if (decoded != null && decoded.isObject()) {
                        shape.append("{").append(fieldNames(decoded)).append("}");
                    } else if (decoded != null) {
                        shape.append("->").append(decoded.getNodeType());
                    }
                } catch (JacksonException ignored) {
                    shape.append("(invalid-json)");
                }
            } else if (node.isObject()) {
                shape.append("{").append(fieldNames(node)).append("}");
            } else if (node.isArray()) {
                shape.append("[").append(node.size()).append("]");
                JsonNode first = node.path(0);
                if (first.isObject()) {
                    shape.append("{").append(fieldNames(first)).append("}");
                } else if (!first.isMissingNode()) {
                    shape.append("->").append(first.getNodeType());
                }
            }
            shapes.add(shape.toString());
        }
        return String.join(",", shapes);
    }

    private ObjectNode merge(JsonNode envelope, JsonNode... nested) {
        ObjectNode merged = mapper.createObjectNode();
        if (envelope instanceof ObjectNode object) {
            merged.setAll(object);
        }
        for (JsonNode node : nested) {
            if (node instanceof ObjectNode object) {
                merged.setAll(object);
            }
        }
        return merged;
    }

    private void validateNestedObjectShape(JsonNode root, String field) {
        JsonNode node = root.path(field);
        if (node.isMissingNode() || node.isNull() || node.isObject()) {
            return;
        }
        if (node.isTextual()) {
            try {
                JsonNode decoded = mapper.readTree(node.asText());
                if (decoded != null && decoded.isObject()) {
                    return;
                }
            } catch (JacksonException ignored) {
                // Fall through to the schema error below.
            }
            throw new OfficialUsageSchemaException(field + " 不是有效 JSON 对象");
        }
        throw new OfficialUsageSchemaException(field + " 必须为 JSON 对象");
    }

    /**
     * SLS can return a structured log item with the provider payload nested in
     * a textual `message` field. Accept both that shape and the legacy flat
     * shape used by the audit logstore.
     */
    private JsonNode unwrapSlsMessage(JsonNode root) {
        JsonNode message = root.path("message");
        if (!message.isTextual() || message.asText().isBlank()) {
            return root;
        }
        try {
            JsonNode decoded = mapper.readTree(message.asText());
            return decoded != null && decoded.isObject() ? decoded : root;
        } catch (JacksonException exception) {
            return root;
        }
    }

    private static String fieldNames(JsonNode root) {
        var names = new java.util.ArrayList<String>();
        names.addAll(root.propertyNames());
        return String.join(",", names);
    }
}
