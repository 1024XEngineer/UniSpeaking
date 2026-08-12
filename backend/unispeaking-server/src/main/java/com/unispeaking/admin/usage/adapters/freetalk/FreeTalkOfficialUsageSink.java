package com.unispeaking.admin.usage.adapters.freetalk;

import com.unispeaking.admin.usage.application.UsageSourceUnavailableException;
import com.unispeaking.admin.usage.domain.ModelUsage;
import com.unispeaking.admin.usage.domain.OfficialUsageRecord;
import com.unispeaking.admin.usage.ports.OfficialUsageSink;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@ConditionalOnProperty(name = "unispeaking.integrations.freetalk.enabled", havingValue = "true")
public final class FreeTalkOfficialUsageSink implements OfficialUsageSink {
    private final RestClient client;

    @Autowired
    public FreeTalkOfficialUsageSink(
            @Value("${unispeaking.integrations.freetalk.base-url:http://127.0.0.1:8000}") String baseUrl) {
        this(RestClient.builder(), baseUrl);
    }

    FreeTalkOfficialUsageSink(RestClient.Builder builder, String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
    }

    @Override
    public ImportResult importRecords(List<OfficialUsageRecord> records) {
        try {
            var response = client.post()
                    .uri("/api/admin/usage/provider-records/import")
                    .body(records.stream().map(FreeTalkOfficialUsageSink::payload).toList())
                    .retrieve()
                    .body(ImportResult.class);
            if (response == null) {
                throw new UsageSourceUnavailableException("FreeTalk 官方用量导入返回空响应", null);
            }
            return response;
        } catch (RestClientException exception) {
            throw new UsageSourceUnavailableException("FreeTalk 官方用量导入失败", exception);
        }
    }

    private static Map<String, Object> payload(OfficialUsageRecord record) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("request_id", record.requestId());
        payload.put("task_uuid", record.taskUuid());
        payload.put("start_unix_timestamp", Long.toString(record.startedAtEpochMs()));
        payload.put("duration", Long.toString(record.durationMs()));
        payload.put("status_code", record.statusCode());
        payload.put("model", record.model());
        payload.put("workspace_id", record.workspaceId());
        payload.put("apikey_id", record.apiKeyId());
        payload.put("extras", Map.of("protocol", record.protocol()));
        payload.put("usage", usagePayload(record.usage()));
        return payload;
    }

    private static Map<String, Object> usagePayload(ModelUsage usage) {
        var result = new LinkedHashMap<String, Object>();
        result.put("total_tokens", usage.totalTokens());
        result.put("input_tokens", usage.inputTokens());
        result.put("output_tokens", usage.outputTokens());
        result.put("input_tokens_details", Map.of(
                "text_tokens", usage.inputTextTokens(),
                "audio_tokens", usage.inputAudioTokens()));
        result.put("output_tokens_details", Map.of(
                "text_tokens", usage.outputTextTokens(),
                "audio_tokens", usage.outputAudioTokens()));
        return result;
    }
}
