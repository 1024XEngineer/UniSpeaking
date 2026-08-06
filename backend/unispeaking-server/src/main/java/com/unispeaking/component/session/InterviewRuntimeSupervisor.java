package com.unispeaking.component.session;

import com.unispeaking.domain.po.session.AbstractSceneSession;
import com.unispeaking.domain.po.session.InterviewSession;
import com.unispeaking.domain.po.session.InterviewSubmission;
import com.unispeaking.domain.vo.session.SessionStatus;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class InterviewRuntimeSupervisor implements AutoCloseable {

	private static final Logger LOGGER = LoggerFactory.getLogger(
			InterviewRuntimeSupervisor.class);

	private final ActiveSessionRegistry sessions;
	private final InterviewExecutionCoordinator coordinator;
	private final Clock clock;
	private final InterviewRuntimePolicy policy;
	private final ScheduledExecutorService scheduler;
	private final List<ExpiredInterviewCleanup> cleanupPorts;
	private final List<ExpiredInterviewSubmissionCleanup> submissionCleanupPorts;
	private final ConcurrentHashMap<SubmissionCleanupKey, PendingSubmissionCleanup>
			pendingSubmissionCleanups = new ConcurrentHashMap<>();
	private ScheduledFuture<?> scanTask;

	public InterviewRuntimeSupervisor(
			ActiveSessionRegistry sessions,
			InterviewExecutionCoordinator coordinator,
			@Qualifier("interviewClock") Clock clock,
			InterviewRuntimePolicy policy,
			@Qualifier("interviewWatchdogScheduler") ScheduledExecutorService scheduler,
			List<ExpiredInterviewCleanup> cleanupPorts,
			List<ExpiredInterviewSubmissionCleanup> submissionCleanupPorts) {
		this.sessions = Objects.requireNonNull(sessions, "sessions");
		this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
		this.clock = Objects.requireNonNull(clock, "clock");
		this.policy = Objects.requireNonNull(policy, "policy");
		this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
		this.cleanupPorts = List.copyOf(
				Objects.requireNonNull(cleanupPorts, "cleanupPorts"));
		this.submissionCleanupPorts = List.copyOf(
				Objects.requireNonNull(submissionCleanupPorts, "submissionCleanupPorts"));
	}

	@PostConstruct
	public synchronized void start() {
		if (scanTask != null && !scanTask.isCancelled() && !scanTask.isDone()) {
			return;
		}
		long intervalMillis = policy.scanInterval().toMillis();
		scanTask = scheduler.scheduleAtFixedRate(
				this::scanSafely,
				intervalMillis,
				intervalMillis,
				TimeUnit.MILLISECONDS);
	}

	public Optional<InterviewSession> recordStateQuery(String sessionId) {
		return recordActivity(sessionId);
	}

	public Optional<InterviewSession> recordHeartbeat(String sessionId) {
		return recordActivity(sessionId);
	}

	public Optional<InterviewSession> recordBusinessActivity(String sessionId) {
		return recordActivity(sessionId);
	}

	public int scan() {
		Instant now = clock.instant();
		retryPendingSubmissionCleanups();
		int changes = coordinator.expireTasks(now);
		for (AbstractSceneSession candidate : sessions.snapshot()) {
			if (!(candidate instanceof InterviewSession session)) {
				continue;
			}
			try {
				changes += expireOrphanedSubmissions(session, now);
				changes += scanSession(session, now);
			} catch (RuntimeException | Error exception) {
				LOGGER.warn(
						"Interview runtime scan failed sessionId={} interviewId={}",
						session.getId(),
						session.interviewId(),
						exception);
			}
		}
		return changes;
	}

	private int expireOrphanedSubmissions(InterviewSession session, Instant now) {
		int expired = 0;
		for (InterviewSubmission submission : session.submissions()) {
			Instant deadline = submission.acceptedAt().plus(policy.taskTimeout());
			if (!submission.status().isInFlight() || now.isBefore(deadline)) {
				continue;
			}
			ExpiredInterviewSubmissionCleanupRequest request =
					new ExpiredInterviewSubmissionCleanupRequest(
							session.getId(),
							session.interviewId(),
							submission.submissionId(),
							submission.acceptedAt(),
							deadline);
			SubmissionCleanupKey key = new SubmissionCleanupKey(
					request.sessionId(), request.submissionId());
			PendingSubmissionCleanup pending = new PendingSubmissionCleanup(request);
			if (pendingSubmissionCleanups.putIfAbsent(key, pending) != null) {
				continue;
			}
			if (!submission.markTimedOut(
					true,
					"INTERVIEW_SUBMISSION_TIMEOUT",
					"Interview submission exceeded its processing deadline",
					now)) {
				pendingSubmissionCleanups.remove(key, pending);
				continue;
			}
			pending.markReady();
			expired++;
			attemptSubmissionCleanup(key, pending);
		}
		return expired;
	}

	private void retryPendingSubmissionCleanups() {
		for (var entry : List.copyOf(pendingSubmissionCleanups.entrySet())) {
			attemptSubmissionCleanup(entry.getKey(), entry.getValue());
		}
	}

	private void attemptSubmissionCleanup(
			SubmissionCleanupKey key,
			PendingSubmissionCleanup pending) {
		if (!pending.tryStart()) {
			return;
		}
		boolean completed = false;
		try {
			completed = publishSubmissionCleanup(pending.request());
		} finally {
			if (completed) {
				pendingSubmissionCleanups.remove(key, pending);
			} else {
				pending.finishAttempt();
			}
		}
	}

	public synchronized boolean isRunning() {
		return scanTask != null && !scanTask.isCancelled() && !scanTask.isDone();
	}

	@PreDestroy
	@Override
	public synchronized void close() {
		if (scanTask != null) {
			scanTask.cancel(false);
			scanTask = null;
		}
	}

	private Optional<InterviewSession> recordActivity(String sessionId) {
		String requiredId = requireSessionId(sessionId);
		Optional<InterviewSession> found = sessions.findById(
				requiredId, InterviewSession.class);
		if (found.isEmpty()) {
			found = sessions.snapshot().stream()
					.filter(InterviewSession.class::isInstance)
					.map(InterviewSession.class::cast)
					.filter(session -> session.interviewId().equals(requiredId))
					.findFirst();
		}
		if (found.isEmpty()) {
			return Optional.empty();
		}
		InterviewSession session = found.orElseThrow();
		Instant now = clock.instant();
		synchronized (session) {
			if (isTerminal(session.getStatus())) {
				return Optional.empty();
			}
			if (session.expirationCleanupClaimed()) {
				return Optional.empty();
			}
			if (session.getStatus() == SessionStatus.INTERRUPTED) {
				Instant deadline = session.interruptedAt()
						.orElse(session.lastSeen())
						.plus(policy.recoveryWindow());
				if (!now.isBefore(deadline)) {
					return Optional.empty();
				}
				session.resume();
			}
			session.touch(now);
		}
		return Optional.of(session);
	}

	private int scanSession(InterviewSession session, Instant now) {
		if (coordinator.hasPendingTimeoutCallback(session.interviewId())
				|| hasPendingSubmissionCleanup(session.interviewId())) {
			return 0;
		}
		int[] changes = new int[1];
		boolean idle = coordinator.runIfIdle(
				session.interviewId(),
				() -> changes[0] = scanIdleSession(session, now));
		return idle ? changes[0] : 0;
	}

	private int scanIdleSession(InterviewSession session, Instant now) {
		if (session.hasInFlightSubmissions()) {
			return 0;
		}
		ExpiredInterviewCleanupRequest cleanupRequest = null;
		boolean interrupted = false;
		synchronized (session) {
			SessionStatus status = session.getStatus();
			if (isTerminal(status)) {
				return 0;
			}
			Instant lastSeen = session.lastSeen();
			if (status == SessionStatus.INTERRUPTED
					&& !now.isBefore(session.interruptedAt()
							.orElse(lastSeen)
							.plus(policy.recoveryWindow()))) {
				if (!session.claimExpirationCleanup()) {
					return 0;
				}
				cleanupRequest = new ExpiredInterviewCleanupRequest(
						session.getId(),
						session.interviewId(),
						session.getUserId(),
						lastSeen,
						now);
			} else if (status != SessionStatus.INTERRUPTED
					&& !now.isBefore(lastSeen.plus(policy.idleTimeout()))) {
				session.recordInterrupt(now);
				interrupted = true;
			}
		}
		if (cleanupRequest == null) {
			return interrupted ? 1 : 0;
		}
		if (!publishCleanup(cleanupRequest)) {
			session.releaseExpirationCleanupClaim();
			return interrupted ? 1 : 0;
		}
		return sessions.remove(session.getId(), session) ? (interrupted ? 2 : 1) : 0;
	}

	private boolean hasPendingSubmissionCleanup(String interviewId) {
		return pendingSubmissionCleanups.values().stream()
				.anyMatch(pending -> pending.request().interviewId().equals(interviewId));
	}

	private boolean publishCleanup(ExpiredInterviewCleanupRequest request) {
		boolean successful = true;
		for (ExpiredInterviewCleanup cleanup : cleanupPorts) {
			try {
				cleanup.cleanup(request);
			} catch (RuntimeException | Error exception) {
				successful = false;
				LOGGER.warn(
						"Expired interview cleanup callback failed sessionId={} interviewId={}",
						request.sessionId(),
						request.interviewId(),
						exception);
			}
		}
		return successful;
	}

	private boolean publishSubmissionCleanup(
			ExpiredInterviewSubmissionCleanupRequest request) {
		boolean successful = true;
		for (ExpiredInterviewSubmissionCleanup cleanup : submissionCleanupPorts) {
			try {
				cleanup.cleanup(request);
			} catch (RuntimeException | Error exception) {
				successful = false;
				LOGGER.warn(
						"Expired submission cleanup callback failed sessionId={} "
								+ "interviewId={} submissionId={}",
						request.sessionId(),
						request.interviewId(),
						request.submissionId(),
						exception);
			}
		}
		return successful;
	}

	private void scanSafely() {
		try {
			scan();
		} catch (RuntimeException | Error exception) {
			LOGGER.warn("Interview runtime scan failed", exception);
		}
	}

	private static boolean isTerminal(SessionStatus status) {
		return status == SessionStatus.COMPLETED || status == SessionStatus.FAILED;
	}

	private static String requireSessionId(String sessionId) {
		if (sessionId == null || sessionId.isBlank()) {
			throw new IllegalArgumentException("sessionId must not be blank");
		}
		return sessionId.trim();
	}

	private record SubmissionCleanupKey(String sessionId, String submissionId) { }

	private static final class PendingSubmissionCleanup {

		private final ExpiredInterviewSubmissionCleanupRequest request;
		private final AtomicBoolean running = new AtomicBoolean();
		private volatile boolean ready;

		private PendingSubmissionCleanup(ExpiredInterviewSubmissionCleanupRequest request) {
			this.request = request;
		}

		private ExpiredInterviewSubmissionCleanupRequest request() { return request; }
		private void markReady() { ready = true; }
		private boolean tryStart() { return ready && running.compareAndSet(false, true); }
		private void finishAttempt() { running.set(false); }
	}
}
