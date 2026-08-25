package com.unispeaking.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    @Test
    void validatesConfigurationLeaseLifecycleAndDailyRollover() {
        assertThatThrownBy(() -> new InMemoryGatewayQuota(null, Duration.ofSeconds(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InMemoryGatewayQuota(Clock.systemUTC(), null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InMemoryGatewayQuota(Clock.systemUTC(), Duration.ofSeconds(-1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InMemoryGatewayQuota(Clock.systemUTC(), Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);

        Instant firstDay = Instant.parse("2026-08-07T00:00:00Z");
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenReturn(firstDay);
        var quota = new InMemoryGatewayQuota(clock, Duration.ofSeconds(10));
        assertThatThrownBy(() -> quota.reserve(null, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> quota.reserve(" ", 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> quota.reserve("user", 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> quota.start("missing")).isInstanceOf(GatewayException.class).hasMessage("LEASE_NOT_FOUND");
        var unstarted = quota.reserve("other", 1);
        assertThatThrownBy(() -> quota.settle(unstarted.leaseId(), firstDay)).isInstanceOf(GatewayException.class).hasMessage("LEASE_NOT_SETTLEABLE");
        var lease = quota.reserve("user", 5);
        quota.start(lease.leaseId());
        assertThatThrownBy(() -> quota.start(lease.leaseId())).isInstanceOf(GatewayException.class).hasMessage("LEASE_NOT_STARTABLE");
        assertThatThrownBy(() -> quota.settle(lease.leaseId(), null)).isInstanceOf(GatewayException.class).hasMessage("LEASE_NOT_SETTLEABLE");
        assertThatThrownBy(() -> quota.settle(lease.leaseId(), firstDay.minusSeconds(1))).isInstanceOf(GatewayException.class).hasMessage("LEASE_NOT_SETTLEABLE");
        quota.settle(lease.leaseId(), firstDay.plusSeconds(20));
        assertThatThrownBy(() -> quota.settle(lease.leaseId(), firstDay.plusSeconds(20))).isInstanceOf(GatewayException.class).hasMessage("LEASE_NOT_SETTLEABLE");
        assertThatThrownBy(() -> quota.start(lease.leaseId())).isInstanceOf(GatewayException.class).hasMessage("LEASE_NOT_STARTABLE");
        assertThat(quota.remainingSeconds("user")).isEqualTo(5);
        when(clock.instant()).thenReturn(firstDay.plus(Duration.ofDays(1)));
        assertThat(quota.remainingSeconds("user")).isEqualTo(10);
    }
}
