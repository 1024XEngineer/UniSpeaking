package com.unispeaking.auth;

@FunctionalInterface
public interface AliyunCaptchaClient {

    boolean verify(String sceneId, String captchaVerifyParam);
}
