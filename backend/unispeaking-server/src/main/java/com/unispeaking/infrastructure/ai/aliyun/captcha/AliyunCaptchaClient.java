package com.unispeaking.infrastructure.ai.aliyun.captcha;

@FunctionalInterface
public interface AliyunCaptchaClient {

    boolean verify(String sceneId, String captchaVerifyParam);
}
