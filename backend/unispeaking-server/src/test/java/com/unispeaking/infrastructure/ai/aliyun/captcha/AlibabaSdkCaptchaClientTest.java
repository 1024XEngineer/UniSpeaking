package com.unispeaking.infrastructure.ai.aliyun.captcha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aliyun.sdk.service.captcha20230305.AsyncClient;
import com.aliyun.sdk.service.captcha20230305.models.VerifyIntelligentCaptchaRequest;
import com.aliyun.sdk.service.captcha20230305.models.VerifyIntelligentCaptchaResponse;
import com.aliyun.sdk.service.captcha20230305.models.VerifyIntelligentCaptchaResponseBody;
import java.lang.reflect.Field;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class AlibabaSdkCaptchaClientTest {

    @Test
    void buildsRequestAndAcceptsSuccessfulVerification() throws Exception {
        var sdk = mock(AsyncClient.class);
        when(sdk.verifyIntelligentCaptcha(any())).thenReturn(
                CompletableFuture.completedFuture(response(true, true)));
        var client = client(sdk);

        assertThat(client.verify("scene-1", "opaque-param")).isTrue();

        var request = org.mockito.Mockito.mockingDetails(sdk).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("verifyIntelligentCaptcha"))
                .map(invocation -> (VerifyIntelligentCaptchaRequest) invocation.getArguments()[0])
                .findFirst()
                .orElseThrow();
        assertThat(request.getSceneId()).isEqualTo("scene-1");
        assertThat(request.getCaptchaVerifyParam()).isEqualTo("opaque-param");
    }

    @Test
    void rejectsEveryIncompleteOrNegativeSdkResponse() throws Exception {
        assertThat(clientReturning(response(false, true)).verify("scene", "param")).isFalse();
        assertThat(clientReturning(response(true, false)).verify("scene", "param")).isFalse();
        assertThat(clientReturning(response(true, null)).verify("scene", "param")).isFalse();
        assertThat(clientReturning(response(null, true)).verify("scene", "param")).isFalse();
        assertThat(clientReturning(null).verify("scene", "param")).isFalse();

        var sdk = mock(AsyncClient.class);
        when(sdk.verifyIntelligentCaptcha(any())).thenReturn(
                CompletableFuture.completedFuture(responseWithNullBody()));
        assertThat(client(sdk).verify("scene", "param")).isFalse();
    }

    @Test
    void closesTheUnderlyingSdkClient() throws Exception {
        var sdk = mock(AsyncClient.class);
        var client = client(sdk);

        client.close();

        verify(sdk).close();
    }

    private AlibabaSdkCaptchaClient clientReturning(VerifyIntelligentCaptchaResponse response)
            throws Exception {
        var sdk = mock(AsyncClient.class);
        when(sdk.verifyIntelligentCaptcha(any())).thenReturn(CompletableFuture.completedFuture(response));
        return client(sdk);
    }

    private AlibabaSdkCaptchaClient client(AsyncClient sdk) throws Exception {
        var client = new AlibabaSdkCaptchaClient(
                "access-key-id", "access-key-secret", "cn-hangzhou", "https://captcha.aliyuncs.com");
        Field field = AlibabaSdkCaptchaClient.class.getDeclaredField("client");
        field.setAccessible(true);
        field.set(client, sdk);
        return client;
    }

    private static VerifyIntelligentCaptchaResponse response(Boolean success, Boolean verifyResult) {
        var result = verifyResult == null
                ? VerifyIntelligentCaptchaResponseBody.Result.builder().build()
                : VerifyIntelligentCaptchaResponseBody.Result.builder().verifyResult(verifyResult).build();
        var body = VerifyIntelligentCaptchaResponseBody.builder()
                .success(success)
                .result(result)
                .build();
        VerifyIntelligentCaptchaResponse.Builder builder = VerifyIntelligentCaptchaResponse.create().toBuilder();
        return builder.body(body).statusCode(200).build();
    }

    private static VerifyIntelligentCaptchaResponse responseWithNullBody() {
        VerifyIntelligentCaptchaResponse.Builder builder = VerifyIntelligentCaptchaResponse.create().toBuilder();
        return builder.statusCode(200).build();
    }
}
