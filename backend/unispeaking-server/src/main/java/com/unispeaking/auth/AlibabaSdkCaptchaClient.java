package com.unispeaking.auth;

import com.aliyun.auth.credentials.Credential;
import com.aliyun.auth.credentials.provider.StaticCredentialProvider;
import com.aliyun.sdk.service.captcha20230305.AsyncClient;
import com.aliyun.sdk.service.captcha20230305.models.VerifyIntelligentCaptchaRequest;
import darabonba.core.client.ClientOverrideConfiguration;
import java.time.Duration;

/** Thin adapter around Alibaba CAPTCHA 2.0 server SDK. */
public final class AlibabaSdkCaptchaClient implements AliyunCaptchaClient, AutoCloseable {

    private final AsyncClient client;

    public AlibabaSdkCaptchaClient(String accessKeyId, String accessKeySecret, String region, String endpoint) {
        var credential = Credential.builder()
                .accessKeyId(accessKeyId)
                .accessKeySecret(accessKeySecret)
                .build();
        var override = ClientOverrideConfiguration.create()
                .setEndpointOverride(endpoint)
                .setConnectTimeout(Duration.ofSeconds(5))
                .setResponseTimeout(Duration.ofSeconds(8));
        this.client = AsyncClient.builder()
                .credentialsProvider(StaticCredentialProvider.create(credential))
                .region(region)
                .overrideConfiguration(override)
                .build();
    }

    @Override
    public boolean verify(String sceneId, String captchaVerifyParam) {
        var request = VerifyIntelligentCaptchaRequest.builder()
                .sceneId(sceneId)
                .captchaVerifyParam(captchaVerifyParam)
                .build();
        var response = client.verifyIntelligentCaptcha(request).join();
        var body = response == null ? null : response.getBody();
        var result = body == null ? null : body.getResult();
        return body != null
                && Boolean.TRUE.equals(body.getSuccess())
                && result != null
                && Boolean.TRUE.equals(result.getVerifyResult());
    }

    @Override
    public void close() {
        client.close();
    }
}
