package com.unispeaking.auth;

@FunctionalInterface
public interface HumanVerificationGateway {

    boolean verify(String token);
}
