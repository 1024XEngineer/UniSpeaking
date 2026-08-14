package com.unispeaking.infrastructure.ai.aliyun.captcha;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import com.unispeaking.infrastructure.ai.aliyun.captcha.AliyunCaptchaClient;
import com.unispeaking.infrastructure.ai.aliyun.captcha.AliyunHumanVerificationGateway;
import org.junit.jupiter.api.Test;

class AliyunHumanVerificationGatewayTest {

    @Test
    void sendsTheSceneAndOpaqueParameterToAliyun() {
        var capturedScene = new AtomicReference<String>();
        var capturedParameter = new AtomicReference<String>();
        AliyunCaptchaClient client = (sceneId, captchaVerifyParam) -> {
            capturedScene.set(sceneId);
            capturedParameter.set(captchaVerifyParam);
            return true;
        };

        var gateway = new AliyunHumanVerificationGateway(client, "i12nr63f");

        assertThat(gateway.verify("opaque-captcha-parameter")).isTrue();
        assertThat(capturedScene).hasValue("i12nr63f");
        assertThat(capturedParameter).hasValue("opaque-captcha-parameter");
    }

    @Test
    void rejectsBlankParametersWithoutCallingAliyun() {
        var called = new AtomicReference<>(false);
        AliyunCaptchaClient client = (sceneId, captchaVerifyParam) -> {
            called.set(true);
            return true;
        };

        var gateway = new AliyunHumanVerificationGateway(client, "i12nr63f");

        assertThat(gateway.verify(" ")).isFalse();
        assertThat(called).hasValue(false);
    }

    @Test
    void failsClosedWhenAliyunIsUnavailable() {
        AliyunCaptchaClient client = (sceneId, captchaVerifyParam) -> {
            throw new IllegalStateException("provider unavailable");
        };

        var gateway = new AliyunHumanVerificationGateway(client, "i12nr63f");

        assertThat(gateway.verify("opaque-captcha-parameter")).isFalse();
    }
}
