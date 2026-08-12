package com.unispeaking.auth;

import org.springframework.util.StringUtils;

/** Verifies the opaque parameter issued by the browser-side Alibaba CAPTCHA widget. */
public final class AliyunHumanVerificationGateway implements HumanVerificationGateway {

    private final AliyunCaptchaClient client;
    private final String sceneId;

    public AliyunHumanVerificationGateway(AliyunCaptchaClient client, String sceneId) {
        this.client = client;
        this.sceneId = sceneId;
    }

    @Override
    public boolean verify(String captchaVerifyParam) {
        if (!StringUtils.hasText(sceneId) || !StringUtils.hasText(captchaVerifyParam)) {
            return false;
        }
        try {
            return client.verify(sceneId, captchaVerifyParam);
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
