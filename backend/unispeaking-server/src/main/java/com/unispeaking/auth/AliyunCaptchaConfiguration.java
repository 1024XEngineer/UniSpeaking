package com.unispeaking.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "unispeaking.auth.captcha.provider", havingValue = "aliyun")
public class AliyunCaptchaConfiguration {

    @Bean(destroyMethod = "close")
    AlibabaSdkCaptchaClient aliyunCaptchaClient(
            @Value("${unispeaking.auth.captcha.access-key-id:}") String accessKeyId,
            @Value("${unispeaking.auth.captcha.access-key-secret:}") String accessKeySecret,
            @Value("${unispeaking.auth.captcha.region:cn-shanghai}") String region,
            @Value("${unispeaking.auth.captcha.endpoint:captcha.cn-shanghai.aliyuncs.com}") String endpoint) {
        if (!org.springframework.util.StringUtils.hasText(accessKeyId)
                || !org.springframework.util.StringUtils.hasText(accessKeySecret)) {
            throw new IllegalStateException("Alibaba CAPTCHA credentials are not configured");
        }
        return new AlibabaSdkCaptchaClient(accessKeyId, accessKeySecret, region, endpoint);
    }

    @Bean
    AliyunHumanVerificationGateway aliyunHumanVerificationGateway(
            AlibabaSdkCaptchaClient client,
            @Value("${unispeaking.auth.captcha.scene-id:}") String sceneId) {
        return new AliyunHumanVerificationGateway(client, sceneId);
    }
}
