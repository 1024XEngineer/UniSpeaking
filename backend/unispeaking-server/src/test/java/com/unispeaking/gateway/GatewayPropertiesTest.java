package com.unispeaking.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class GatewayPropertiesTest {

    @Test
    void exposesValidatedDurationsAtBothCredentialBounds() {
        GatewayProperties minimum = new GatewayProperties(true, 1, 2, 3);
        GatewayProperties maximum = new GatewayProperties(false, 1800, 4, 5);

        assertEquals(Duration.ofSeconds(1), minimum.credentialTtl());
        assertEquals(Duration.ofSeconds(2), minimum.sessionLease());
        assertEquals(Duration.ofSeconds(3), minimum.keyFailureCooldown());
        assertEquals(Duration.ofMinutes(30), maximum.credentialTtl());
    }

    @Test
    void rejectsCredentialTtlOutsideBothBounds() {
        assertThrows(IllegalArgumentException.class,
                () -> new GatewayProperties(true, 0, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new GatewayProperties(true, 1801, 1, 1));
    }

    @Test
    void rejectsNonPositiveLeaseAndCooldown() {
        assertThrows(IllegalArgumentException.class,
                () -> new GatewayProperties(true, 1, 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new GatewayProperties(true, 1, 1, 0));
    }
}
