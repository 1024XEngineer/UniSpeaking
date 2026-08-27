package com.unispeaking.admin.usage.adapters.aliyun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AliyunInferenceLogParserTest {
    private final AliyunInferenceLogParser parser = new AliyunInferenceLogParser(new ObjectMapper());

    @Test
    void parsesRealtimeInferenceUsageIntoOfficialDimensions() {
        var record = parser.parse("""
                {
                  "start_unix_timestamp": "1784534676105",
                  "first_output_duration": "0",
                  "status_code": "200",
                  "usage": {
                    "input_tokens_details": {"text_tokens": 12840, "audio_tokens": 98},
                    "total_tokens": 13289,
                    "output_tokens": 351,
                    "input_tokens": 12938,
                    "output_tokens_details": {"audio_tokens": 266, "text_tokens": 85}
                  },
                  "channel": "default",
                  "extras": {"encrypt": "disable", "model_type": "BASE_MODEL", "protocol": "ws", "sub_protocol": "DEFAULT"},
                  "apikey_id": "6124876",
                  "source": "model",
                  "duration": "50962",
                  "workspace_id": "ws-y8vkj6bzloynsltz",
                  "start_time": "2026-07-20 16:04:36.105",
                  "uid": "1859063776324206",
                  "model": "qwen3.5-omni-flash-realtime",
                  "client_ip": "10.0.46.237",
                  "task_uuid": "sess_41tAWSq7xIR1b2EDelEgS",
                  "request_id": "3131bedf-7956-91c3-90fe-27cbcb3dfbcf"
                }
                """);

        assertThat(record.requestId()).isEqualTo("3131bedf-7956-91c3-90fe-27cbcb3dfbcf");
        assertThat(record.taskUuid()).isEqualTo("sess_41tAWSq7xIR1b2EDelEgS");
        assertThat(record.startedAtEpochMs()).isEqualTo(1784534676105L);
        assertThat(record.durationMs()).isEqualTo(50962L);
        assertThat(record.statusCode()).isEqualTo("200");
        assertThat(record.model()).isEqualTo("qwen3.5-omni-flash-realtime");
        assertThat(record.workspaceId()).isEqualTo("ws-y8vkj6bzloynsltz");
        assertThat(record.apiKeyId()).isEqualTo("6124876");
        assertThat(record.protocol()).isEqualTo("ws");

        var usage = record.usage();
        assertThat(usage.responseCount()).isEqualTo(1);
        assertThat(usage.inputTokens()).isEqualTo(12938);
        assertThat(usage.outputTokens()).isEqualTo(351);
        assertThat(usage.totalTokens()).isEqualTo(13289);
        assertThat(usage.inputTextTokens()).isEqualTo(12840);
        assertThat(usage.inputAudioTokens()).isEqualTo(98);
        assertThat(usage.outputTextTokens()).isEqualTo(85);
        assertThat(usage.outputAudioTokens()).isEqualTo(266);
    }

    @Test
    void rejectsRecordsWhoseTokenDimensionsDoNotAddUp() {
        assertThatThrownBy(() -> parser.parse(validJson()
                .replace("\"input_tokens\": 12938", "\"input_tokens\": 12939")))
                .isInstanceOf(OfficialUsageSchemaException.class)
                .hasMessageContaining("input_tokens");

        assertThatThrownBy(() -> parser.parse(validJson()
                .replace("\"total_tokens\": 13289", "\"total_tokens\": 13290")))
                .isInstanceOf(OfficialUsageSchemaException.class)
                .hasMessageContaining("total_tokens");
    }

    @Test
    void rejectsRecordsWithoutStableMatchingIdentifiers() {
        assertThatThrownBy(() -> parser.parse(validJson()
                .replace("sess_41tAWSq7xIR1b2EDelEgS", "")))
                .isInstanceOf(OfficialUsageSchemaException.class)
                .hasMessageContaining("task_uuid");

        assertThatThrownBy(() -> parser.parse(validJson()
                .replace("3131bedf-7956-91c3-90fe-27cbcb3dfbcf", "")))
                .isInstanceOf(OfficialUsageSchemaException.class)
                .hasMessageContaining("request_id");
    }

    @Test
    void rejectsNegativeDurationAndUnexpectedProtocol() {
        assertThatThrownBy(() -> parser.parse(validJson().replace("\"duration\": \"50962\"", "\"duration\": \"-1\"")))
                .isInstanceOf(OfficialUsageSchemaException.class)
                .hasMessageContaining("duration");

        assertThatThrownBy(() -> parser.parse(validJson().replace("\"protocol\": \"ws\"", "\"protocol\": \"ftp\"")))
                .isInstanceOf(OfficialUsageSchemaException.class)
                .hasMessageContaining("protocol");
    }

    @Test
    void parsesNestedObjectsWhenSlsReturnsThemAsJsonStrings() {
        var record = parser.parse("""
                {
                  "start_unix_timestamp":"1784534676105",
                  "status_code":"200",
                  "usage":"{\\\"input_tokens_details\\\":{\\\"text_tokens\\\":12840,\\\"audio_tokens\\\":98},\\\"total_tokens\\\":13289,\\\"output_tokens\\\":351,\\\"input_tokens\\\":12938,\\\"output_tokens_details\\\":{\\\"audio_tokens\\\":266,\\\"text_tokens\\\":85}}",
                  "extras":"{\\\"protocol\\\":\\\"ws\\\"}",
                  "apikey_id":"6124876",
                  "duration":"50962",
                  "workspace_id":"ws-y8vkj6bzloynsltz",
                  "model":"qwen3.5-omni-flash-realtime",
                  "task_uuid":"sess_41tAWSq7xIR1b2EDelEgS",
                  "request_id":"3131bedf-7956-91c3-90fe-27cbcb3dfbcf"
                }
                """);

        assertThat(record.usage().inputTokens()).isEqualTo(12938);
        assertThat(record.protocol()).isEqualTo("ws");
    }

    @Test
    void unwrapsProviderPayloadWhenSlsWrapsItInMessage() {
        String payload = validJson().replace("\n", " ").replace("\"", "\\\"");
        var record = parser.parse("{\"message\":\"" + payload + "\"}");

        assertThat(record.requestId()).isEqualTo("3131bedf-7956-91c3-90fe-27cbcb3dfbcf");
        assertThat(record.protocol()).isEqualTo("ws");
        assertThat(record.usage().totalTokens()).isEqualTo(13289);
    }

    @Test
    void parsesHttpLlmUsageWithoutTaskUuid() {
        var record = parser.parse("""
                {
                  "start_unix_timestamp":"1787106794285",
                  "status_code":"200",
                  "usage":{"input_tokens":970,"output_tokens":177,"total_tokens":1147,
                    "input_tokens_details":{"text_tokens":970},
                    "output_tokens_details":{"text_tokens":177}},
                  "extras":{"protocol":"http"},
                  "apikey_id":"6126227",
                  "duration":"3566",
                  "workspace_id":"ws-67zfnonsdn4x96ia",
                  "model":"qwen3.5-plus",
                  "request_id":"400cacba-b89c-959b-80cc-1aa25c4c6ff5"
                }
                """);

        assertThat(record.taskUuid()).isNull();
        assertThat(record.protocol()).isEqualTo("http");
        assertThat(record.usage().totalTokens()).isEqualTo(1147);
        assertThat(record.characters()).isZero();
    }

    @Test
    void parsesHttpTtsCharactersWithoutTokenFields() {
        var record = parser.parse("""
                {
                  "start_unix_timestamp":"1787106742708",
                  "status_code":"200",
                  "usage":{"characters":51},
                  "extras":{"protocol":"HTTP"},
                  "apikey_id":"6126227",
                  "duration":"889",
                  "workspace_id":"ws-67zfnonsdn4x96ia",
                  "model":"qwen3-tts-flash",
                  "request_id":"932b0d1e-3568-9b6a-8d9e-936e89418b54"
                }
                """);

        assertThat(record.protocol()).isEqualTo("http");
        assertThat(record.characters()).isEqualTo(51);
        assertThat(record.usage().totalTokens()).isZero();
    }

    @Test
    void acceptsFailedHttpRecordsWithoutUsageAndNonWsProtocolsWithoutTaskUuid() {
        var record = parser.parse("""
                {
                  "start_unix_timestamp":"1787106742708",
                  "status_code":"500",
                  "extras":{"protocol":"HTTP"},
                  "apikey_id":"6126227",
                  "duration":"0",
                  "workspace_id":"ws-67zfnonsdn4x96ia",
                  "model":"qwen3-tts-flash",
                  "request_id":"request-failed-1"
                }
                """);

        assertThat(record.statusCode()).isEqualTo("500");
        assertThat(record.taskUuid()).isNull();
        assertThat(record.usage().responseCount()).isZero();
        assertThat(record.usage().totalTokens()).isZero();
    }

    @Test
    void acceptsWebrtcWithTaskUuidAndOptionalZeroDetails() {
        var record = parser.parse("""
                {
                  "start_unix_timestamp":"1787106742708",
                  "status_code":"200",
                  "usage":{"input_tokens":0,"output_tokens":0,"total_tokens":0,
                    "input_tokens_details":{},"output_tokens_details":{}},
                  "extras":{"protocol":"webrtc"},
                  "apikey_id":"6126227",
                  "duration":"0",
                  "workspace_id":"ws-67zfnonsdn4x96ia",
                  "model":"qwen3.5-omni-flash-realtime",
                  "task_uuid":"task-1",
                  "request_id":"request-webrtc-1"
                }
                """);

        assertThat(record.protocol()).isEqualTo("webrtc");
        assertThat(record.taskUuid()).isEqualTo("task-1");
        assertThat(record.usage().inputTextTokens()).isZero();
        assertThat(record.usage().outputAudioTokens()).isZero();
    }

    @Test
    void rejectsMalformedNestedObjectsMissingRequiredFieldsAndInvalidJson() {
        assertThatThrownBy(() -> parser.parse("not-json"))
                .isInstanceOf(OfficialUsageSchemaException.class);
        assertThatThrownBy(() -> parser.parse(validJson()
                .replace("\"extras\": {\"protocol\": \"ws\"}", "\"extras\": []")))
                .isInstanceOf(OfficialUsageSchemaException.class)
                .hasMessageContaining("extras");
        assertThatThrownBy(() -> parser.parse(validJson()
                .replace("\"usage\": {", "\"usage\": \"not-json\"")))
                .isInstanceOf(OfficialUsageSchemaException.class);
        assertThatThrownBy(() -> parser.parse(validJson()
                .replace("\"model\": \"qwen3.5-omni-flash-realtime\"", "\"model\": \"\"")))
                .isInstanceOf(OfficialUsageSchemaException.class)
                .hasMessageContaining("model");
    }

    @Test
    void rejectsNegativeTimestampAndMissingRealtimeTaskUuid() {
        assertThatThrownBy(() -> parser.parse(validJson()
                .replace("\"start_unix_timestamp\": \"1784534676105\"", "\"start_unix_timestamp\": \"-1\"")))
                .isInstanceOf(OfficialUsageSchemaException.class)
                .hasMessageContaining("start_unix_timestamp");
        assertThatThrownBy(() -> parser.parse(validJson()
                .replace("\"task_uuid\": \"sess_41tAWSq7xIR1b2EDelEgS\",", "")))
                .isInstanceOf(OfficialUsageSchemaException.class)
                .hasMessageContaining("task_uuid");
        assertThatThrownBy(() -> parser.parse(validJson()
                .replace("\"protocol\": \"ws\"", "\"protocol\": \"webrtc\"")
                .replace("\"task_uuid\": \"sess_41tAWSq7xIR1b2EDelEgS\",", "")))
                .isInstanceOf(OfficialUsageSchemaException.class)
                .hasMessageContaining("task_uuid");
    }

    private static String validJson() {
        return """
                {
                  "start_unix_timestamp": "1784534676105",
                  "status_code": "200",
                  "usage": {
                    "input_tokens_details": {"text_tokens": 12840, "audio_tokens": 98},
                    "total_tokens": 13289,
                    "output_tokens": 351,
                    "input_tokens": 12938,
                    "output_tokens_details": {"audio_tokens": 266, "text_tokens": 85}
                  },
                  "extras": {"protocol": "ws"},
                  "apikey_id": "6124876",
                  "duration": "50962",
                  "workspace_id": "ws-y8vkj6bzloynsltz",
                  "model": "qwen3.5-omni-flash-realtime",
                  "task_uuid": "sess_41tAWSq7xIR1b2EDelEgS",
                  "request_id": "3131bedf-7956-91c3-90fe-27cbcb3dfbcf"
                }
                """;
    }
}
