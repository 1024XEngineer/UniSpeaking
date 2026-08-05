package com.unispeaking.component.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unispeaking.domain.po.session.InterviewSession;
import com.unispeaking.domain.po.session.InterviewSubmission;
import com.unispeaking.domain.vo.scene.InterviewDifficulty;
import com.unispeaking.domain.vo.scene.InterviewPlannedQuestion;
import com.unispeaking.domain.vo.scene.InterviewQuestionPlan;
import com.unispeaking.domain.vo.scene.InterviewSubmissionStatus;
import com.unispeaking.domain.vo.session.SessionStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class InterviewRuntimeSupervisorTest {

	private static final Instant START = Instant.parse("2030-01-01T00:00:00Z");
	private static final InterviewRuntimePolicy POLICY = InterviewRuntimePolicy.defaults();

	@Test
	void stateHeartbeatAndBusinessActivityRefreshLastSeenAndResumeWithoutChangingProgress() {
		MutableClock clock = new MutableClock(START);
		ActiveSessionRegistry registry = new ActiveSessionRegistry();
		InterviewSession session = activeSession("session_1", "interview_1", clock);
		session.recordMainQuestion("First question");
		var questions = session.actualQuestions();
		registry.save(session);
		InterviewRuntimeSupervisor supervisor = supervisor(
				registry, directCoordinator(clock), clock, List.of());

		clock.advance(POLICY.idleTimeout());
		assertEquals(1, supervisor.scan());
		assertEquals(SessionStatus.INTERRUPTED, session.getStatus());

		clock.advance(Duration.ofMinutes(1));
		assertSame(session, supervisor.recordStateQuery("session_1").orElseThrow());
		assertEquals(SessionStatus.ACTIVE, session.getStatus());
		assertEquals(clock.instant(), session.lastSeen());
		assertEquals(questions, session.actualQuestions());

		clock.advance(Duration.ofSeconds(1));
		assertTrue(supervisor.recordHeartbeat("interview_1").isPresent());
		assertEquals(clock.instant(), session.lastSeen());
		clock.advance(Duration.ofSeconds(1));
		assertTrue(supervisor.recordBusinessActivity("session_1").isPresent());
		assertEquals(clock.instant(), session.lastSeen());
		assertEquals(questions, session.actualQuestions());
	}

	@Test
	void acceptedProcessingAndFinalizingSessionsAreExcludedFromIdleCleanup() {
		MutableClock clock = new MutableClock(START);
		ActiveSessionRegistry registry = new ActiveSessionRegistry();
		List<Runnable> drains = new CopyOnWriteArrayList<>();
		InterviewExecutionCoordinator coordinator = new InterviewExecutionCoordinator(
				drains::add, clock, () -> { });
		InterviewSession accepted = activeSession("session_a", "interview_a", clock);
		accepted.recordMainQuestion("Question");
		accepted.registerSubmission(new InterviewSubmission(
				"submission_a", 1, "digest_a", clock.instant()));
		InterviewSession processing = activeSession("session_p", "interview_p", clock);
		InterviewSession finalizing = activeSession("session_f", "interview_f", clock);
		registry.save(accepted);
		registry.save(processing);
		registry.save(finalizing);
		coordinator.execute("interview_p", InterviewTaskType.PROCESSING, () -> { });
		coordinator.execute("interview_f", InterviewTaskType.FINALIZING, () -> { });
		InterviewRuntimeSupervisor supervisor = supervisor(
				registry, coordinator, clock, List.of());

		clock.advance(Duration.ofMinutes(9));
		assertEquals(0, supervisor.scan());
		assertEquals(SessionStatus.ACTIVE, accepted.getStatus());
		assertEquals(SessionStatus.ACTIVE, processing.getStatus());
		assertEquals(SessionStatus.ACTIVE, finalizing.getStatus());
		assertEquals(3, registry.snapshot().size());
	}

	@Test
	void watchdogFailsQueuedSubmissionCleansTemporaryDataAndReleasesRuntime() {
		MutableClock clock = new MutableClock(START);
		ActiveSessionRegistry registry = new ActiveSessionRegistry();
		List<Runnable> drains = new ArrayList<>();
		InterviewExecutionCoordinator coordinator = new InterviewExecutionCoordinator(
				drains::add, clock, () -> { });
		InterviewSession session = activeSession("session_1", "interview_1", clock);
		session.recordMainQuestion("Question");
		InterviewSubmission submission = new InterviewSubmission(
				"submission_1", 1, "digest_1", clock.instant());
		session.registerSubmission(submission);
		registry.save(session);
		AtomicInteger providerCalls = new AtomicInteger();
		AtomicInteger temporaryCleanups = new AtomicInteger();
		List<ExpiredInterviewCleanupRequest> cleanups = new ArrayList<>();
		coordinator.executeSubmission(
				"interview_1",
				submission,
					lease -> providerCalls.incrementAndGet(),
				temporaryCleanups::incrementAndGet);
		InterviewRuntimeSupervisor supervisor = supervisor(
				registry, coordinator, clock, List.of(cleanups::add));

		clock.advance(POLICY.taskTimeout());
		assertEquals(2, supervisor.scan());
		assertEquals(InterviewSubmissionStatus.FAILED_RETRYABLE, submission.status());
		assertEquals("INTERVIEW_SUBMISSION_TIMEOUT", submission.errorCode());
		assertEquals(1, temporaryCleanups.get());
		assertFalse(coordinator.isBusy("interview_1"));
		assertEquals(SessionStatus.INTERRUPTED, session.getStatus());
		assertTrue(registry.findById("session_1").isPresent());
		assertTrue(cleanups.isEmpty());

		clock.advance(POLICY.recoveryWindow());
		assertEquals(1, supervisor.scan());
		assertTrue(registry.findById("session_1").isEmpty());
		assertEquals("interview_1", cleanups.getFirst().interviewId());

		drains.forEach(Runnable::run);
		assertEquals(0, providerCalls.get());
	}

	@Test
	void orphanedAcceptedSubmissionExpiresFromItsAcceptanceTime() {
		MutableClock clock = new MutableClock(START);
		ActiveSessionRegistry registry = new ActiveSessionRegistry();
		InterviewSession session = activeSession("session_1", "interview_1", clock);
		session.recordMainQuestion("Question");
		InterviewSubmission submission = new InterviewSubmission(
				"submission_1", 1, "digest_1", clock.instant());
		session.registerSubmission(submission);
		registry.save(session);
		List<ExpiredInterviewSubmissionCleanupRequest> submissionCleanups =
				new ArrayList<>();
		AtomicReference<InterviewSubmissionStatus> statusAtCleanup = new AtomicReference<>();
		List<ExpiredInterviewCleanupRequest> interviewCleanups = new ArrayList<>();
		InterviewRuntimeSupervisor supervisor = new InterviewRuntimeSupervisor(
				registry,
				directCoordinator(clock),
				clock,
				POLICY,
				mock(ScheduledExecutorService.class),
				List.of(interviewCleanups::add),
				List.of(request -> {
					statusAtCleanup.set(submission.status());
					submissionCleanups.add(request);
				}));

		clock.advance(POLICY.taskTimeout());
		assertEquals(2, supervisor.scan());

		assertEquals(InterviewSubmissionStatus.FAILED_RETRYABLE, submission.status());
		assertEquals(InterviewSubmissionStatus.FAILED_RETRYABLE, statusAtCleanup.get());
		assertEquals(1, submissionCleanups.size());
		assertEquals("submission_1", submissionCleanups.getFirst().submissionId());
		assertEquals(START.plus(POLICY.taskTimeout()),
				submissionCleanups.getFirst().deadline());
		assertTrue(interviewCleanups.isEmpty());
		assertTrue(registry.findById("session_1").isPresent());

		clock.advance(POLICY.recoveryWindow());
		assertEquals(1, supervisor.scan());
		assertEquals(1, interviewCleanups.size());
		assertTrue(registry.findById("session_1").isEmpty());
	}

	@Test
	void failedOrphanedSubmissionCleanupIsRetriedBeforeSessionCanExpire() {
		MutableClock clock = new MutableClock(START);
		ActiveSessionRegistry registry = new ActiveSessionRegistry();
		InterviewSession session = activeSession("session_1", "interview_1", clock);
		session.recordMainQuestion("Question");
		InterviewSubmission submission = new InterviewSubmission(
				"submission_1", 1, "digest_1", clock.instant());
		session.registerSubmission(submission);
		registry.save(session);
		AtomicInteger cleanupAttempts = new AtomicInteger();
		InterviewRuntimeSupervisor supervisor = new InterviewRuntimeSupervisor(
				registry,
				directCoordinator(clock),
				clock,
				POLICY,
				mock(ScheduledExecutorService.class),
				List.of(),
				List.of(request -> {
					if (cleanupAttempts.incrementAndGet() == 1) {
						throw new IllegalStateException("temporary cleanup unavailable");
					}
				}));

		clock.advance(POLICY.taskTimeout());
		assertEquals(1, supervisor.scan());
		assertEquals(InterviewSubmissionStatus.FAILED_RETRYABLE, submission.status());
		assertEquals(1, cleanupAttempts.get());
		assertEquals(SessionStatus.ACTIVE, session.getStatus());
		assertTrue(registry.findById("session_1").isPresent());

		assertEquals(1, supervisor.scan());
		assertEquals(2, cleanupAttempts.get());
		assertEquals(SessionStatus.INTERRUPTED, session.getStatus());
		assertTrue(registry.findById("session_1").isPresent());
	}

	@Test
	void failedSubmissionTimeoutCleanupIsRetriedBeforeRuntimeCanExpire() {
		MutableClock clock = new MutableClock(START);
		ActiveSessionRegistry registry = new ActiveSessionRegistry();
		List<Runnable> drains = new ArrayList<>();
		InterviewExecutionCoordinator coordinator = new InterviewExecutionCoordinator(
				drains::add, clock, () -> { });
		InterviewSession session = activeSession("session_1", "interview_1", clock);
		session.recordMainQuestion("Question");
		InterviewSubmission submission = new InterviewSubmission(
				"submission_1", 1, "digest_1", clock.instant());
		session.registerSubmission(submission);
		registry.save(session);
		AtomicInteger cleanupAttempts = new AtomicInteger();
		coordinator.executeSubmission(
				"interview_1",
				submission,
					lease -> { },
				() -> {
					if (cleanupAttempts.incrementAndGet() == 1) {
						throw new IllegalStateException("temporary cleanup unavailable");
					}
				});
		InterviewRuntimeSupervisor supervisor = supervisor(
				registry, coordinator, clock, List.of());

		clock.advance(POLICY.taskTimeout());
		assertEquals(1, supervisor.scan());
		assertEquals(InterviewSubmissionStatus.FAILED_RETRYABLE, submission.status());
		assertFalse(coordinator.isBusy("interview_1"));
		assertTrue(coordinator.hasPendingTimeoutCallback("interview_1"));
		assertTrue(registry.findById("session_1").isPresent());
		assertEquals(SessionStatus.ACTIVE, session.getStatus());

		assertEquals(1, supervisor.scan());
		assertEquals(2, cleanupAttempts.get());
		assertFalse(coordinator.hasPendingTimeoutCallback("interview_1"));
		assertEquals(SessionStatus.INTERRUPTED, session.getStatus());
		assertTrue(registry.findById("session_1").isPresent());
	}

	@Test
	void runningCleanupRetryRemainsVisibleToConcurrentScans() throws Exception {
		MutableClock clock = new MutableClock(START);
		ActiveSessionRegistry registry = new ActiveSessionRegistry();
		List<Runnable> drains = new ArrayList<>();
		InterviewExecutionCoordinator coordinator = new InterviewExecutionCoordinator(
				drains::add, clock, () -> { });
		InterviewSession session = activeSession("session_1", "interview_1", clock);
		session.recordMainQuestion("Question");
		InterviewSubmission submission = new InterviewSubmission(
				"submission_1", 1, "digest_1", clock.instant());
		session.registerSubmission(submission);
		registry.save(session);
		AtomicInteger cleanupAttempts = new AtomicInteger();
		CountDownLatch retryStarted = new CountDownLatch(1);
		CountDownLatch releaseRetry = new CountDownLatch(1);
		coordinator.executeSubmission(
				"interview_1",
				submission,
					lease -> { },
				() -> {
					int attempt = cleanupAttempts.incrementAndGet();
					if (attempt == 1) {
						throw new IllegalStateException("temporary cleanup unavailable");
					}
					if (attempt == 2) {
						retryStarted.countDown();
						await(releaseRetry);
						throw new AssertionError("temporary cleanup still unavailable");
					}
				});
		InterviewRuntimeSupervisor supervisor = supervisor(
				registry, coordinator, clock, List.of());
		clock.advance(POLICY.taskTimeout());
		assertEquals(1, supervisor.scan());

		ExecutorService retryExecutor = Executors.newSingleThreadExecutor();
		try {
			Future<Integer> retryScan = retryExecutor.submit(supervisor::scan);
			assertTrue(retryStarted.await(5, TimeUnit.SECONDS));
			assertTrue(coordinator.hasPendingTimeoutCallback("interview_1"));

			assertEquals(0, supervisor.scan());
			assertEquals(SessionStatus.ACTIVE, session.getStatus());
			assertTrue(registry.findById("session_1").isPresent());

			releaseRetry.countDown();
			assertEquals(0, retryScan.get(5, TimeUnit.SECONDS));
		} finally {
			releaseRetry.countDown();
			retryExecutor.shutdownNow();
		}

		assertTrue(coordinator.hasPendingTimeoutCallback("interview_1"));
		assertEquals(1, supervisor.scan());
		assertFalse(coordinator.hasPendingTimeoutCallback("interview_1"));
		assertEquals(SessionStatus.INTERRUPTED, session.getStatus());
		assertTrue(registry.findById("session_1").isPresent());
	}

	@Test
	void cleanupFailureIsIsolatedAndRetriedSessionRemainsRegistered() {
		MutableClock clock = new MutableClock(START);
		ActiveSessionRegistry registry = new ActiveSessionRegistry();
		InterviewSession failedCleanup = activeSession("session_1", "interview_1", clock);
		InterviewSession successfulCleanup = activeSession("session_2", "interview_2", clock);
		registry.save(failedCleanup);
		registry.save(successfulCleanup);
		List<String> attempted = new CopyOnWriteArrayList<>();
		ExpiredInterviewCleanup cleanup = request -> {
			attempted.add(request.sessionId());
			if (request.sessionId().equals("session_1")) {
				throw new IllegalStateException("downstream unavailable");
			}
		};
		InterviewRuntimeSupervisor supervisor = supervisor(
				registry, directCoordinator(clock), clock, List.of(cleanup));

		clock.advance(POLICY.idleTimeout());
		supervisor.scan();
		clock.advance(POLICY.recoveryWindow());
		supervisor.scan();

		assertEquals(2, attempted.size());
		assertTrue(registry.findById("session_1").isPresent());
		assertTrue(registry.findById("session_2").isEmpty());
		assertEquals(SessionStatus.INTERRUPTED, failedCleanup.getStatus());
	}

	@Test
	void recoveryWindowBoundaryExpiresInsteadOfResuming() {
		MutableClock clock = new MutableClock(START);
		ActiveSessionRegistry registry = new ActiveSessionRegistry();
		InterviewSession session = activeSession("session_1", "interview_1", clock);
		registry.save(session);
		InterviewRuntimeSupervisor supervisor = supervisor(
				registry, directCoordinator(clock), clock, List.of());

		clock.advance(POLICY.idleTimeout());
		supervisor.scan();
		clock.advance(POLICY.recoveryWindow());

		assertTrue(supervisor.recordHeartbeat("session_1").isEmpty());
		assertEquals(SessionStatus.INTERRUPTED, session.getStatus());
	}

	@Test
	void interruptedSessionCanRecoverForTheFullRecoveryWindow() {
		MutableClock clock = new MutableClock(START);
		ActiveSessionRegistry registry = new ActiveSessionRegistry();
		InterviewSession session = activeSession("session_1", "interview_1", clock);
		registry.save(session);
		InterviewRuntimeSupervisor supervisor = supervisor(
				registry, directCoordinator(clock), clock, List.of());

		clock.advance(POLICY.idleTimeout());
		supervisor.scan();
		Instant interruptedAt = clock.instant();
		clock.advance(POLICY.recoveryWindow().minusMillis(1));

		assertSame(session, supervisor.recordHeartbeat("session_1").orElseThrow());
		assertEquals(SessionStatus.ACTIVE, session.getStatus());
		assertTrue(session.interruptedAt().isEmpty());
		assertEquals(interruptedAt.plus(POLICY.recoveryWindow()).minusMillis(1),
				session.lastSeen());
	}

	@Test
	void concurrentIdleScanAndHeartbeatCannotLeaveTouchedSessionInterrupted() throws Exception {
		MutableClock clock = new MutableClock(START);
		ActiveSessionRegistry registry = new ActiveSessionRegistry();
		InterviewSession session = activeSession("session_1", "interview_1", clock);
		registry.save(session);
		InterviewRuntimeSupervisor supervisor = supervisor(
				registry, directCoordinator(clock), clock, List.of());
		clock.advance(POLICY.idleTimeout());
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<?> scan = executor.submit(() -> {
				await(start);
				supervisor.scan();
			});
			Future<?> heartbeat = executor.submit(() -> {
				await(start);
				supervisor.recordHeartbeat("session_1");
			});
			start.countDown();
			scan.get(5, TimeUnit.SECONDS);
			heartbeat.get(5, TimeUnit.SECONDS);
		} finally {
			executor.shutdownNow();
		}

		assertEquals(SessionStatus.ACTIVE, session.getStatus());
		assertEquals(clock.instant(), session.lastSeen());
		assertSame(session, registry.findById("session_1").orElseThrow());
	}

	@Test
	void heartbeatCannotReviveSessionAfterExpirationCleanupIsClaimed() throws Exception {
		MutableClock clock = new MutableClock(START);
		ActiveSessionRegistry registry = new ActiveSessionRegistry();
		InterviewSession session = activeSession("session_1", "interview_1", clock);
		registry.save(session);
		CountDownLatch cleanupStarted = new CountDownLatch(1);
		CountDownLatch releaseCleanup = new CountDownLatch(1);
		InterviewRuntimeSupervisor supervisor = supervisor(
				registry,
				directCoordinator(clock),
				clock,
				List.of(request -> {
					cleanupStarted.countDown();
					await(releaseCleanup);
				}));
		clock.advance(POLICY.idleTimeout());
		assertEquals(1, supervisor.scan());
		clock.advance(POLICY.recoveryWindow());

		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			Future<Integer> cleanup = executor.submit(supervisor::scan);
			assertTrue(cleanupStarted.await(5, TimeUnit.SECONDS));
			assertTrue(session.expirationCleanupClaimed());
			assertTrue(supervisor.recordHeartbeat("session_1").isEmpty());

			releaseCleanup.countDown();
			assertEquals(1, cleanup.get(5, TimeUnit.SECONDS));
		} finally {
			releaseCleanup.countDown();
			executor.shutdownNow();
		}

		assertTrue(registry.findById("session_1").isEmpty());
		assertEquals(SessionStatus.INTERRUPTED, session.getStatus());
	}

	@Test
	void scheduledScanStartsOnceAndCloseCancelsItsFuture() {
		MutableClock clock = new MutableClock(START);
		ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
		@SuppressWarnings("unchecked")
		ScheduledFuture<Object> future = mock(ScheduledFuture.class);
		AtomicReference<Runnable> scheduledScan = new AtomicReference<>();
		when(future.isCancelled()).thenReturn(false);
		when(scheduler.scheduleAtFixedRate(
				any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class)))
				.thenAnswer(invocation -> {
					scheduledScan.set(invocation.getArgument(0));
					return future;
				});
		InterviewRuntimeSupervisor supervisor = new InterviewRuntimeSupervisor(
				new ActiveSessionRegistry(),
				directCoordinator(clock),
				clock,
				POLICY,
				scheduler,
				List.of(),
				List.of());

		supervisor.start();
		supervisor.start();
		assertTrue(supervisor.isRunning());
		scheduledScan.get().run();
		verify(scheduler, times(1)).scheduleAtFixedRate(
				any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));

		supervisor.close();
		verify(future).cancel(false);
		assertFalse(supervisor.isRunning());
	}

	private static InterviewRuntimeSupervisor supervisor(
			ActiveSessionRegistry registry,
			InterviewExecutionCoordinator coordinator,
			Clock clock,
			List<ExpiredInterviewCleanup> cleanups) {
		return new InterviewRuntimeSupervisor(
				registry,
				coordinator,
				clock,
				POLICY,
				mock(ScheduledExecutorService.class),
				cleanups,
				List.of());
	}

	private static InterviewExecutionCoordinator directCoordinator(Clock clock) {
		return new InterviewExecutionCoordinator(Runnable::run, clock, () -> { });
	}

	private static InterviewSession activeSession(
			String sessionId,
			String interviewId,
			MutableClock clock) {
		InterviewSession session = new InterviewSession(
				sessionId, "user_1", interviewId, plan());
		session.activate();
		session.touch(clock.instant());
		return session;
	}

	private static InterviewQuestionPlan plan() {
		return new InterviewQuestionPlan(
				InterviewDifficulty.STANDARD,
				IntStream.rangeClosed(1, 5)
						.mapToObj(no -> new InterviewPlannedQuestion(no, "Main " + no, 1))
						.toList());
	}

	private static void await(CountDownLatch latch) {
		try {
			if (!latch.await(5, TimeUnit.SECONDS)) {
				throw new IllegalStateException("timed out waiting for runtime test");
			}
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("runtime test interrupted", exception);
		}
	}

	private static final class MutableClock extends Clock {

		private final AtomicReference<Instant> now;

		private MutableClock(Instant now) {
			this.now = new AtomicReference<>(now);
		}

		private void advance(Duration duration) {
			now.updateAndGet(current -> current.plus(duration));
		}

		@Override
		public ZoneId getZone() { return ZoneOffset.UTC; }

		@Override
		public Clock withZone(ZoneId zone) {
			return zone.equals(ZoneOffset.UTC) ? this : Clock.fixed(instant(), zone);
		}

		@Override
		public Instant instant() { return now.get(); }
	}
}
