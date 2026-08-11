package com.unispeaking.gateway;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Local-only daily quota ledger. Replace with a transactional store later. */
public final class InMemoryGatewayQuota {
    private final Clock clock;
    private final long dailyLimitSeconds;
    private final Map<String, Account> accounts = new HashMap<>();
    private final Map<String, Lease> leases = new HashMap<>();

    public InMemoryGatewayQuota(Clock clock, Duration dailyLimit) {
        if (clock == null || dailyLimit == null || dailyLimit.isNegative() || dailyLimit.isZero()) {
            throw new IllegalArgumentException("quota configuration is invalid");
        }
        this.clock = clock;
        this.dailyLimitSeconds = dailyLimit.toSeconds();
    }

    public synchronized GatewayLease reserve(String userId, long requestedSeconds) {
        if (userId == null || userId.isBlank() || requestedSeconds <= 0) {
            throw new IllegalArgumentException("userId and requestedSeconds are required");
        }
        Account account = account(userId);
        long available = dailyLimitSeconds - account.consumedSeconds - account.reservedSeconds;
        if (requestedSeconds > available) {
            throw new GatewayException("QUOTA_EXCEEDED");
        }
        String leaseId = UUID.randomUUID().toString();
        account.reservedSeconds += requestedSeconds;
        leases.put(leaseId, new Lease(leaseId, userId, requestedSeconds, null, false));
        return new GatewayLease(leaseId, userId, requestedSeconds);
    }

    public synchronized void start(String leaseId) {
        Lease lease = requiredLease(leaseId);
        if (lease.startedAt != null || lease.settled) {
            throw new GatewayException("LEASE_NOT_STARTABLE");
        }
        lease.startedAt = clock.instant();
    }

    public synchronized void settle(String leaseId, Instant endedAt) {
        Lease lease = requiredLease(leaseId);
        if (lease.startedAt == null || lease.settled || endedAt == null || endedAt.isBefore(lease.startedAt)) {
            throw new GatewayException("LEASE_NOT_SETTLEABLE");
        }
        long usedSeconds = Math.min(lease.reservedSeconds,
                Math.max(0, Duration.between(lease.startedAt, endedAt).toSeconds()));
        Account account = account(lease.userId);
        account.reservedSeconds -= lease.reservedSeconds;
        account.consumedSeconds += usedSeconds;
        lease.settled = true;
    }

    public synchronized long remainingSeconds(String userId) {
        Account account = account(userId);
        return dailyLimitSeconds - account.consumedSeconds - account.reservedSeconds;
    }

    private Account account(String userId) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        Account account = accounts.computeIfAbsent(userId, ignored -> new Account(today));
        if (!account.day.equals(today)) {
            account.day = today;
            account.consumedSeconds = 0;
            account.reservedSeconds = 0;
        }
        return account;
    }

    private Lease requiredLease(String leaseId) {
        Lease lease = leases.get(leaseId);
        if (lease == null) {
            throw new GatewayException("LEASE_NOT_FOUND");
        }
        return lease;
    }

    private static final class Account {
        private LocalDate day;
        private long consumedSeconds;
        private long reservedSeconds;

        private Account(LocalDate day) {
            this.day = day;
        }
    }

    private static final class Lease {
        private final String leaseId;
        private final String userId;
        private final long reservedSeconds;
        private Instant startedAt;
        private boolean settled;

        private Lease(String leaseId, String userId, long reservedSeconds, Instant startedAt, boolean settled) {
            this.leaseId = leaseId;
            this.userId = userId;
            this.reservedSeconds = reservedSeconds;
            this.startedAt = startedAt;
            this.settled = settled;
        }
    }

    public record GatewayLease(String leaseId, String userId, long reservedSeconds) {
    }
}
