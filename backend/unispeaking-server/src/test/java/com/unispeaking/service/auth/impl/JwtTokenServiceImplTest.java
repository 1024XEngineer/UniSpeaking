package com.unispeaking.service.auth.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unispeaking.domain.po.auth.UserAccount;
import com.unispeaking.domain.po.auth.UserRole;
import com.unispeaking.domain.po.auth.UserStatus;
import com.unispeaking.domain.vo.auth.IssuedJwt;
import com.unispeaking.infrastructure.config.JwtProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

class JwtTokenServiceImplTest {
    @Test
    void issuesHs256TokenWithUserClaimsAndConfiguredExpiry() {
        JwtEncoder encoder = mock(JwtEncoder.class);
        Jwt encoded = mock(Jwt.class);
        when(encoded.getTokenValue()).thenReturn("signed-access-token");
        when(encoder.encode(any(JwtEncoderParameters.class))).thenReturn(encoded);

        JwtProperties properties = new JwtProperties();
        properties.setIssuer("test-issuer");
        properties.setAccessTokenTtl(Duration.ofMinutes(20));
        UUID id = UUID.randomUUID();
        UserAccount user = new UserAccount(id, "alice@example.com", "hash", "Alice",
                UserRole.ADMIN, UserStatus.ACTIVE, 7, Instant.now().minusSeconds(30),
                Instant.now().minusSeconds(3600), Instant.now());
        JwtTokenServiceImpl service = new JwtTokenServiceImpl(encoder, properties);

        Instant before = Instant.now();
        IssuedJwt result = service.issue(user);
        Instant after = Instant.now();

        assertEquals("signed-access-token", result.token());
        assertTrue(!result.expiresAt().isBefore(before.plus(properties.getAccessTokenTtl())));
        assertTrue(!result.expiresAt().isAfter(after.plus(properties.getAccessTokenTtl())));

        ArgumentCaptor<JwtEncoderParameters> captor = ArgumentCaptor.forClass(JwtEncoderParameters.class);
        verify(encoder).encode(captor.capture());
        JwtClaimsSet claims = captor.getValue().getClaims();
        assertEquals("test-issuer", claims.getClaimAsString("iss"));
        assertEquals(id.toString(), claims.getSubject());
        assertEquals("alice@example.com", claims.getClaimAsString("username"));
        assertEquals("ADMIN", claims.getClaimAsString("role"));
        assertEquals(Long.valueOf(7L), claims.getClaim("auth_version"));
        assertNotNull(claims.getIssuedAt());
        assertEquals(result.expiresAt(), claims.getExpiresAt());
        assertEquals("HS256", captor.getValue().getJwsHeader().getAlgorithm().getName());
    }
}
