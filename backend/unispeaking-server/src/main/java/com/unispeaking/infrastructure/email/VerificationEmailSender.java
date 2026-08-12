package com.unispeaking.infrastructure.email;

public interface VerificationEmailSender {

    void sendVerificationCode(String recipient, String code, int ttlSeconds);
}
