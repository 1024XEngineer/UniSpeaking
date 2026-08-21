package com.unispeaking.service.auth;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.domain.dto.auth.AuthResponse;
import com.unispeaking.domain.po.auth.UserAccount;
import com.unispeaking.infrastructure.config.JwtProperties;
import com.unispeaking.infrastructure.persistence.repository.auth.AuthRefreshTokenRepository;
import com.unispeaking.infrastructure.persistence.repository.user.UserAccountRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final AuthRefreshTokenRepository repository;
    private final UserAccountRepository users;
    private final JwtTokenService jwt;
    private final Duration idleTtl;
    private final Duration absoluteTtl;

    public RefreshTokenService(AuthRefreshTokenRepository repository, UserAccountRepository users,
                               JwtTokenService jwt, JwtProperties properties) {
        this.repository = repository;
        this.users = users;
        this.jwt = jwt;
        this.idleTtl = properties.getRefreshIdleTtl();
        this.absoluteTtl = properties.getRefreshAbsoluteTtl();
        if (idleTtl.isNegative() || idleTtl.isZero() || idleTtl.compareTo(Duration.ofDays(30)) > 0)
            throw new IllegalStateException("refresh idle ttl must be between 0 and 30 days");
        if (absoluteTtl.isNegative() || absoluteTtl.isZero())
            throw new IllegalStateException("refresh absolute ttl must be positive");
    }

    public Issued issue(UUID userId) {
        Instant now = Instant.now();
        String raw = randomToken();
        Instant expires = now.plus(absoluteTtl);
        repository.insert(digest(raw), userId, now, expires);
        return new Issued(raw, expires);
    }

    public Issued issue(UserAccount user) {
        return issue(user.id());
    }

    @Transactional
    public Result refresh(String rawToken) {
        Instant now = Instant.now();
        String digest = digest(rawToken);
        var current = repository.find(digest);
        if (current == null || current.revokedAt() != null || !current.expiresAt().isAfter(now)
                || !current.lastUsedAt().plus(idleTtl).isAfter(now)) throw invalid();
        UserAccount user = users.findById(current.userId()).orElseThrow(this::invalid);
        if (user.status() != com.unispeaking.domain.po.auth.UserStatus.ACTIVE) throw invalid();
        if (repository.consume(digest, now, now.minus(idleTtl)) != 1) throw invalid();
        String next = randomToken();
        repository.insert(digest(next), user.id(), now, current.expiresAt());
        var access = jwt.issue(user);
        return new Result(new AuthResponse("Bearer", access.token(), access.expiresAt(),
                com.unispeaking.domain.dto.auth.UserAccountResponse.from(user)), next, current.expiresAt());
    }

    public MobileResult refreshMobile(String rawToken) {
        Result result = refresh(rawToken);
        return new MobileResult(result.access(), result.refreshToken(), result.refreshTokenExpiresAt());
    }

    public void revoke(String rawToken) { if (rawToken != null && !rawToken.isBlank()) repository.revoke(digest(rawToken), Instant.now()); }
    public void revokeAll(UUID userId) { repository.revokeAll(userId, Instant.now()); }
    public int cleanup() { return repository.deleteExpired(Instant.now().minus(Duration.ofDays(1))); }

    private BusinessException invalid() { return new BusinessException("REFRESH_TOKEN_INVALID", "登录状态已失效，请重新登录"); }
    private static String randomToken() { byte[] bytes = new byte[48]; RANDOM.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private static String digest(String raw) { try { return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
    public record Issued(String token, Instant expiresAt) {}
    public record Result(AuthResponse access, String refreshToken, Instant refreshTokenExpiresAt) {}
    public record MobileResult(AuthResponse access, String refreshToken, Instant refreshTokenExpiresAt) {}
}
