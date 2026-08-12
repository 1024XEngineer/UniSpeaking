package com.unispeaking.admin.observability;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class AlibabaObservabilityStatus {
    private final String region;
    private final String project;
    private final String auditLogstore;
    private final String inferenceLogstore;
    private final boolean credentialsConfigured;
    private final boolean prometheusEnabled;

    @Autowired
    public AlibabaObservabilityStatus(
            @Value("${unispeaking.integrations.aliyun.region:cn-beijing}") String region,
            @Value("${unispeaking.integrations.aliyun.project:}") String project,
            @Value("${unispeaking.integrations.aliyun.audit-logstore:bailian-model-audit-log}") String auditLogstore,
            @Value("${unispeaking.integrations.aliyun.inference-logstore:bailian-model-inference-log}") String inferenceLogstore,
            @Value("${unispeaking.integrations.aliyun.access-key-id:}") String accessKeyId,
            @Value("${unispeaking.integrations.aliyun.access-key-secret:}") String accessKeySecret,
            @Value("${unispeaking.integrations.aliyun.prometheus-enabled:false}") boolean prometheusEnabled) {
        this(region, project, auditLogstore, inferenceLogstore,
                !accessKeyId.isBlank() && !accessKeySecret.isBlank(), prometheusEnabled);
    }

    public AlibabaObservabilityStatus(
            String region,
            String project,
            String auditLogstore,
            String inferenceLogstore,
            boolean credentialsConfigured,
            boolean prometheusEnabled) {
        this.region = region;
        this.project = project;
        this.auditLogstore = auditLogstore;
        this.inferenceLogstore = inferenceLogstore;
        this.credentialsConfigured = credentialsConfigured;
        this.prometheusEnabled = prometheusEnabled;
    }

    public String region() { return region; }
    public String project() { return project; }
    public String auditLogstore() { return auditLogstore; }
    public String inferenceLogstore() { return inferenceLogstore; }
    public boolean credentialsConfigured() { return credentialsConfigured; }
    public boolean prometheusEnabled() { return prometheusEnabled; }
}
