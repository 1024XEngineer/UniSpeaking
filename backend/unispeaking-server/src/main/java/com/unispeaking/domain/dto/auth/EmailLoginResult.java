package com.unispeaking.domain.dto.auth;

public record EmailLoginResult(String rawToken, EmailAuthUser user) {
}
