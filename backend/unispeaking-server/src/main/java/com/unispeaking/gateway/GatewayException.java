package com.unispeaking.gateway;

/** Stable error boundary for the local gateway layer. */
public final class GatewayException extends RuntimeException {

    public GatewayException(String code) {
        super(code);
    }
}
