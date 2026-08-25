package com.unispeaking.gateway;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.unispeaking.domain.vo.provider.ProviderType;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class GatewayValueObjectsTest {
    @Test
    void validatesEveryGatewayKeyFieldAndRedactsSecret() {
        for (String keyId : new String[] {null, "", " "}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new GatewayKey(keyId, ProviderType.QWEN, "secret"));
        }
        assertThrows(IllegalArgumentException.class,
                () -> new GatewayKey("key", null, "secret"));
        for (String secret : new String[] {null, "", " "}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new GatewayKey("key", ProviderType.QWEN, secret));
        }
        String rendered = new GatewayKey("key", ProviderType.QWEN, "sensitive").toString();
        assertTrue(rendered.contains("secret=***"));
        assertFalse(rendered.contains("sensitive"));
    }

    @Test
    void validatesEveryCredentialFieldAndExpiryOrderAndRedactsToken() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        Object[][] invalid = {
                {null, "model", "token", now, now.plusSeconds(1), "key"},
                {ProviderType.QWEN, null, "token", now, now.plusSeconds(1), "key"},
                {ProviderType.QWEN, " ", "token", now, now.plusSeconds(1), "key"},
                {ProviderType.QWEN, "model", null, now, now.plusSeconds(1), "key"},
                {ProviderType.QWEN, "model", " ", now, now.plusSeconds(1), "key"},
                {ProviderType.QWEN, "model", "token", null, now.plusSeconds(1), "key"},
                {ProviderType.QWEN, "model", "token", now, null, "key"},
                {ProviderType.QWEN, "model", "token", now, now.plusSeconds(1), null},
                {ProviderType.QWEN, "model", "token", now, now.plusSeconds(1), " "}
        };
        for (Object[] values : invalid) {
            assertThrows(IllegalArgumentException.class, () -> new GatewayCredential(
                    (ProviderType) values[0], (String) values[1], (String) values[2],
                    (Instant) values[3], (Instant) values[4], (String) values[5]));
        }
        assertThrows(IllegalArgumentException.class,
                () -> new GatewayCredential(ProviderType.QWEN, "model", "token", now, now, "key"));
        GatewayCredential credential = new GatewayCredential(
                ProviderType.QWEN, "model", "sensitive", now, now.plusSeconds(1), "key");
        assertFalse(credential.toString().contains("sensitive"));
        assertTrue(credential.toString().contains("bearerToken=***"));
    }
}
