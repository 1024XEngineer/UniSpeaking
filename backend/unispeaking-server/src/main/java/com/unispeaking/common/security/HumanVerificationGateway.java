package com.unispeaking.common.security;

@FunctionalInterface
public interface HumanVerificationGateway {

    boolean verify(String token);
}
