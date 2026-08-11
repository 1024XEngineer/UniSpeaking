package com.unispeaking.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Local-only verifier. Production must replace it with a provider-backed adapter. */
@Component
@ConditionalOnProperty(
        name = "unispeaking.auth.captcha.provider",
        havingValue = "development")
public final class DevelopmentHumanVerificationGateway implements HumanVerificationGateway {

    private final String expectedToken;

    public DevelopmentHumanVerificationGateway(
            @Value("${unispeaking.auth.captcha.development-token:local-human-verified}") String expectedToken) {
        this.expectedToken = expectedToken;
    }

    @Override
    public boolean verify(String token) {
        return expectedToken.equals(token);
    }
}
