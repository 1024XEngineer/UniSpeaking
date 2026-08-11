package com.unispeaking.admin.auth.application;

public final class InvalidSessionException extends RuntimeException {
    public InvalidSessionException() {
        super("invalid session");
    }
}
