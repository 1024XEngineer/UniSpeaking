package com.unispeaking.common.email;

/** Sends an email verification code without exposing a concrete mail provider. */
@FunctionalInterface
public interface VerificationEmailSender {

	void sendVerificationCode(String recipient, String code, int ttlSeconds);
}
