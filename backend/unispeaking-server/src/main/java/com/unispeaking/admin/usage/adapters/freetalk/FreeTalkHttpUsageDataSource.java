package com.unispeaking.admin.usage.adapters.freetalk;

import com.unispeaking.admin.usage.application.UsageSourceUnavailableException;
import com.unispeaking.admin.usage.domain.UsageSnapshot;
import com.unispeaking.admin.usage.ports.UsageDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@ConditionalOnProperty(name = "unispeaking.integrations.freetalk.enabled", havingValue = "true")
public final class FreeTalkHttpUsageDataSource implements UsageDataSource {
    private final RestClient client;

    @Autowired
    public FreeTalkHttpUsageDataSource(
            @Value("${unispeaking.integrations.freetalk.base-url:http://127.0.0.1:8000}") String baseUrl) {
        this(RestClient.builder(), baseUrl);
    }

    FreeTalkHttpUsageDataSource(RestClient.Builder builder, String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
    }

    @Override
    public UsageSnapshot loadSnapshot() {
        try {
            var snapshot = client.get()
                    .uri("/api/admin/usage/users")
                    .retrieve()
                    .body(UsageSnapshot.class);
            if (snapshot == null) {
                throw new UsageSourceUnavailableException("FreeTalk 返回了空响应", null);
            }
            return snapshot;
        } catch (RestClientException exception) {
            throw new UsageSourceUnavailableException("FreeTalk 数据源不可用", exception);
        }
    }

    @Override
    public String sourceCode() { return "FREETALK"; }

    @Override
    public String sourceName() { return "FreeTalk Demo"; }

    @Override
    public String sourceDetail() { return "已停用的演示数据源"; }
}
