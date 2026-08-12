package com.unispeaking.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class InMemoryGatewayQuotaTest {

    @Test
    void reservesAndSettlesSessionSecondsWithoutChargingTheUnusedReservation() {
        var now = Instant.parse("2026-08-07T00:00:00Z");
        var quota = new InMemoryGatewayQuota(
                Clock.fixed(now, ZoneOffset.UTC),
                Duration.ofSeconds(600));

        var lease = quota.reserve("user-1", 600);
        quota.start(lease.leaseId());
        quota.settle(lease.leaseId(), now.plusSeconds(125));

        assertThat(quota.remainingSeconds("user-1")).isEqualTo(475);
    }

    @Test
    void rejectsAReservationLargerThanTheRemainingDailyQuota() {
        var quota = new InMemoryGatewayQuota(
                Clock.fixed(Instant.parse("2026-08-07T00:00:00Z"), ZoneOffset.UTC),
                Duration.ofSeconds(600));

        assertThatThrownBy(() -> quota.reserve("user-1", 601))
                .isInstanceOf(GatewayException.class)
                .hasMessage("QUOTA_EXCEEDED");
    }
}
