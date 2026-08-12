package com.unispeaking.infrastructure.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Keeps local development deterministic without sending real email. */
@Component
@ConditionalOnProperty(name = "unispeaking.auth.email.enabled", havingValue = "false", matchIfMissing = true)
public final class DevelopmentEmailSender implements VerificationEmailSender {

    private static final Logger log = LoggerFactory.getLogger(DevelopmentEmailSender.class);

    @Override
    public void sendVerificationCode(String recipient, String code, int ttlSeconds) {
        log.info("Development email challenge issued recipientDomain={} ttlSeconds={}",
                recipient.substring(recipient.indexOf('@') + 1), ttlSeconds);
    }
}
