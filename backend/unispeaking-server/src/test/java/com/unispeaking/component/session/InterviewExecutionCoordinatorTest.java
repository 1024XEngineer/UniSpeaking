package com.unispeaking.component.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.unispeaking.domain.po.session.InterviewSubmission;
import com.unispeaking.domain.vo.scene.InterviewSubmissionStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class InterviewExecutionCoordinatorTest {

	@Test
	void serializesTasksForTheSameInterviewAndTracksQueuedWork() throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		InterviewExecutionCoordinator coordinator = new InterviewExecutionCoordinator(executor);
		CountDownLatch firstStarted = new CountDownLatch(1);
		CountDownLatch releaseFirst = new CountDownLatch(1);
		CountDownLatch finished = new CountDownLatch(2);
		List<Integer> order = new CopyOnWriteArrayList<>();
		AtomicInteger concurrentTasks = new AtomicInteger();
		AtomicInteger maximumConcurrency = new AtomicInteger();
		try {
			coordinator.execute("interview_1", InterviewTaskType.PROCESSING, () -> {
				trackConcurrency(concurrentTasks, maximumConcurrency);
				order.add(1);
				firstStarted.countDown();
				await(releaseFirst);
				concurrentTasks.decrementAndGet();
				finished.countDown();
			});
			assertTrue(firstStarted.await(5, TimeUnit.SECONDS));
			coordinator.execute("interview_1", InterviewTaskType.FINALIZING, () -> {
				trackConcurrency(concurrentTasks, maximumConcurrency);
				order.add(2);
				concurrentTasks.decrementAndGet();
				finished.countDown();
			});

			InterviewExecutionState queued = coordinator.state("interview_1");
			assertEquals(1, queued.processingTasks());
			assertEquals(1, queued.finalizingTasks());
			assertTrue(queued.busy());
			releaseFirst.countDown();
			assertTrue(finished.await(5, TimeUnit.SECONDS));
			assertEquals(List.of(1, 2), order);
			assertEquals(1, maximumConcurrency.get());
			assertFalse(coordinator.isBusy("interview_1"));
		} finally {
			releaseFirst.countDown();
			executor.shutdownNow();
		}
	}

	@Test
	void allowsDifferentInterviewsToRunInParallel() throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		InterviewExecutionCoordinator coordinator = new InterviewExecutionCoordinator(executor);
		CountDownLatch bothStarted = new CountDownLatch(2);
		CountDownLatch release = new CountDownLatch(1);
		CountDownLatch finished = new CountDownLatch(2);
		try {
			Runnable task = () -> {
				bothStarted.countDown();
				await(release);
				finished.countDown();
			};
			coordinator.execute("interview_1", InterviewTaskType.PROCESSING, task);
			coordinator.execute("interview_2", InterviewTaskType.PROCESSING, task);

			assertTrue(bothStarted.await(5, TimeUnit.SECONDS));
			release.countDown();
			assertTrue(finished.await(5, TimeUnit.SECONDS));
		} finally {
			release.countDown();
			executor.shutdownNow();
		}
	}

	@Test
	void preservesSubmissionOrderForQueuedInterviewTasks() throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(4);
		InterviewExecutionCoordinator coordinator = new InterviewExecutionCoordinator(executor);
		List<Integer> order = new CopyOnWriteArrayList<>();
		CountDownLatch finished = new CountDownLatch(20);
		try {
			for (int index = 0; index < 20; index++) {
				int value = index;
				coordinator.execute("interview_1", InterviewTaskType.PROCESSING, () -> {
					order.add(value);
					finished.countDown();
				});
			}
			assertTrue(finished.await(5, TimeUnit.SECONDS));
			assertEquals(
					List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19),
					order);
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	void rollsBackBusyStateWhenExecutorRejectsTask() {
		InterviewExecutionCoordinator coordinator = new InterviewExecutionCoordinator(
				task -> { throw new RejectedExecutionException("full"); });

		assertThrows(
				RejectedExecutionException.class,
				() -> coordinator.execute(
						"interview_1", InterviewTaskType.PROCESSING, () -> { }));
		assertFalse(coordinator.isBusy("interview_1"));
	}

	@Test
	void taskFailureDoesNotStrandLaterWorkOrBusyState() throws Exception {
		ExecutorService executor = Executors.newSingleThreadExecutor();
		InterviewExecutionCoordinator coordinator = new InterviewExecutionCoordinator(
				task -> executor.submit(task));
		CountDownLatch firstStarted = new CountDownLatch(1);
		CountDownLatch releaseFirst = new CountDownLatch(1);
		CountDownLatch secondFinished = new CountDownLatch(1);
		try {
			coordinator.execute("interview_1", InterviewTaskType.PROCESSING, () -> {
				firstStarted.countDown();
				await(releaseFirst);
				throw new IllegalStateException("provider failed");
			});
			assertTrue(firstStarted.await(5, TimeUnit.SECONDS));
			coordinator.execute(
					"interview_1", InterviewTaskType.FINALIZING, secondFinished::countDown);
			releaseFirst.countDown();

			assertTrue(secondFinished.await(5, TimeUnit.SECONDS));
			assertFalse(coordinator.isBusy("interview_1"));
		} finally {
			releaseFirst.countDown();
			executor.shutdownNow();
		}
	}

	@Test
	void synchronousLockUsesTheSamePerInterviewBoundary() throws Exception {
		ExecutorService executor = Executors.newSingleThreadExecutor();
		InterviewExecutionCoordinator coordinator = new InterviewExecutionCoordinator(executor);
		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		CountDownLatch asyncFinished = new CountDownLatch(1);
		Thread holder = new Thread(() -> coordinator.withLock("interview_1", () -> {
			locked.countDown();
			await(release);
		}));
		try {
			holder.start();
			assertTrue(locked.await(5, TimeUnit.SECONDS));
			coordinator.execute("interview_1", InterviewTaskType.PROCESSING, asyncFinished::countDown);
			assertFalse(asyncFinished.await(100, TimeUnit.MILLISECONDS));
			release.countDown();
			assertTrue(asyncFinished.await(5, TimeUnit.SECONDS));
			holder.join(5000);
			assertFalse(holder.isAlive());
		} finally {
			release.countDown();
			holder.join(5000);
			executor.shutdownNow();
		}
	}

	@Test
	void busyQueryKeepsItsSlotAcrossReleaseAndNewTaskAcceptance() throws Exception {
		ExecutorService taskExecutor = Executors.newFixedThreadPool(2);
		ExecutorService queryExecutor = Executors.newSingleThreadExecutor();
		CountDownLatch firstTaskStarted = new CountDownLatch(1);
		CountDownLatch releaseFirstTask = new CountDownLatch(1);
		CountDownLatch firstDrainFinished = new CountDownLatch(1);
		CountDownLatch stateRetainedSlot = new CountDownLatch(1);
		CountDownLatch releaseStateRead = new CountDownLatch(1);
		AtomicBoolean blockStateRead = new AtomicBoolean();
		InterviewExecutionCoordinator coordinator = new InterviewExecutionCoordinator(
				task -> taskExecutor.submit(() -> {
					try {
						task.run();
					} finally {
						firstDrainFinished.countDown();
					}
				}),
				() -> {
					if (blockStateRead.compareAndSet(true, false)) {
						stateRetainedSlot.countDown();
						await(releaseStateRead);
					}
				});
		CountDownLatch secondTaskStarted = new CountDownLatch(1);
		CountDownLatch releaseSecondTask = new CountDownLatch(1);
		try {
			coordinator.execute("interview_1", InterviewTaskType.PROCESSING, () -> {
				firstTaskStarted.countDown();
				await(releaseFirstTask);
			});
			assertTrue(firstTaskStarted.await(5, TimeUnit.SECONDS));

			blockStateRead.set(true);
			Future<Boolean> state = queryExecutor.submit(() -> coordinator.isBusy("interview_1"));
			assertTrue(stateRetainedSlot.await(5, TimeUnit.SECONDS));
			releaseFirstTask.countDown();
			assertTrue(firstDrainFinished.await(5, TimeUnit.SECONDS));

			coordinator.execute("interview_1", InterviewTaskType.PROCESSING, () -> {
				secondTaskStarted.countDown();
				await(releaseSecondTask);
			});
			assertTrue(secondTaskStarted.await(5, TimeUnit.SECONDS));
			releaseStateRead.countDown();
			assertTrue(state.get(5, TimeUnit.SECONDS));
		} finally {
			releaseFirstTask.countDown();
			releaseSecondTask.countDown();
			releaseStateRead.countDown();
			taskExecutor.shutdownNow();
			queryExecutor.shutdownNow();
		}
	}

	@Test
	void watchdogCapsDeadlineAndReleasesBusyStateWhileProviderIsBlocked() throws Exception {
		MutableClock clock = new MutableClock(Instant.parse("2030-01-01T00:00:00Z"));
		ExecutorService executor = Executors.newSingleThreadExecutor();
		InterviewExecutionCoordinator coordinator = new InterviewExecutionCoordinator(
				executor, clock, () -> { });
		CountDownLatch providerStarted = new CountDownLatch(1);
		CountDownLatch releaseProvider = new CountDownLatch(1);
		CountDownLatch providerFinished = new CountDownLatch(1);
		AtomicInteger timeouts = new AtomicInteger();
		try {
			coordinator.executeUntil(
					"interview_1",
					InterviewTaskType.PROCESSING,
					clock.instant(),
					Duration.ofHours(1),
					lease -> {
						try {
							providerStarted.countDown();
							await(releaseProvider);
						} finally {
							providerFinished.countDown();
						}
					},
					timeouts::incrementAndGet);
			assertTrue(providerStarted.await(5, TimeUnit.SECONDS));

			clock.advance(Duration.ofMinutes(10).minusMillis(1));
			assertEquals(0, coordinator.expireTasks(clock.instant()));
			assertTrue(coordinator.isBusy("interview_1"));

			clock.advance(Duration.ofMillis(1));
			assertEquals(1, coordinator.expireTasks(clock.instant()));
			assertEquals(1, timeouts.get());
			assertFalse(coordinator.isBusy("interview_1"));
		} finally {
			releaseProvider.countDown();
			assertTrue(providerFinished.await(5, TimeUnit.SECONDS));
			executor.shutdownNow();
		}
	}

	@Test
	void runningSubmissionTimeoutFencesLateCommitAndDefersCleanupUntilTaskStops()
			throws Exception {
		MutableClock clock = new MutableClock(Instant.parse("2030-01-01T00:00:00Z"));
		ExecutorService executor = Executors.newSingleThreadExecutor();
		InterviewExecutionCoordinator coordinator = new InterviewExecutionCoordinator(
				executor, clock, () -> { });
		InterviewSubmission submission = new InterviewSubmission(
				"submission_1", 1, "digest_1", clock.instant());
		CountDownLatch providerStarted = new CountDownLatch(1);
		CountDownLatch releaseProvider = new CountDownLatch(1);
		CountDownLatch cleanupFinished = new CountDownLatch(1);
		AtomicBoolean lateCommitAccepted = new AtomicBoolean(true);
		AtomicInteger lateMutations = new AtomicInteger();
		AtomicInteger temporaryCleanups = new AtomicInteger();
		try {
			coordinator.executeSubmission(
					"interview_1",
					submission,
					lease -> {
						providerStarted.countDown();
						await(releaseProvider);
						lateCommitAccepted.set(lease.commitIfActive(
								lateMutations::incrementAndGet));
					},
					() -> {
						temporaryCleanups.incrementAndGet();
						cleanupFinished.countDown();
					});
			assertTrue(providerStarted.await(5, TimeUnit.SECONDS));

			clock.advance(InterviewRuntimePolicy.MAXIMUM_TASK_TIMEOUT);
			assertEquals(1, coordinator.expireTasks(clock.instant()));
			assertEquals(InterviewSubmissionStatus.FAILED_RETRYABLE, submission.status());
			assertFalse(coordinator.isBusy("interview_1"));
			assertFalse(coordinator.runIfIdle("interview_1", () -> { }));
			assertEquals(0, temporaryCleanups.get());

			releaseProvider.countDown();
			assertTrue(cleanupFinished.await(5, TimeUnit.SECONDS));
			assertFalse(lateCommitAccepted.get());
			assertEquals(0, lateMutations.get());
			assertEquals(1, temporaryCleanups.get());
			assertTrue(coordinator.runIfIdle("interview_1", () -> { }));
		} finally {
			releaseProvider.countDown();
			executor.shutdownNow();
		}
	}

	@Test
	void timeoutCallbackFailureDoesNotBlockOtherExpiredTasks() {
		MutableClock clock = new MutableClock(Instant.parse("2030-01-01T00:00:00Z"));
		List<Runnable> drains = new CopyOnWriteArrayList<>();
		InterviewExecutionCoordinator coordinator = new InterviewExecutionCoordinator(
				drains::add, clock, () -> { });
		AtomicInteger successfulCallbacks = new AtomicInteger();

		coordinator.executeUntil(
				"interview_1",
				InterviewTaskType.PROCESSING,
				clock.instant(),
				Duration.ofMinutes(1),
					lease -> { },
				() -> { throw new IllegalStateException("cleanup failed"); });
		coordinator.executeUntil(
				"interview_2",
				InterviewTaskType.FINALIZING,
				clock.instant(),
				Duration.ofMinutes(1),
					lease -> { },
				successfulCallbacks::incrementAndGet);

		clock.advance(Duration.ofMinutes(1));
		assertEquals(2, coordinator.expireTasks(clock.instant()));
		assertEquals(1, successfulCallbacks.get());
		assertFalse(coordinator.isBusy("interview_1"));
		assertFalse(coordinator.isBusy("interview_2"));
		drains.forEach(Runnable::run);
	}

	@Test
	void failedTimeoutCallbackIsRetriedWithoutRestoringBusyState() {
		MutableClock clock = new MutableClock(Instant.parse("2030-01-01T00:00:00Z"));
		List<Runnable> drains = new CopyOnWriteArrayList<>();
		InterviewExecutionCoordinator coordinator = new InterviewExecutionCoordinator(
				drains::add, clock, () -> { });
		AtomicInteger attempts = new AtomicInteger();
		coordinator.executeUntil(
				"interview_1",
				InterviewTaskType.PROCESSING,
				clock.instant(),
				Duration.ofMinutes(1),
					lease -> { },
				() -> {
					if (attempts.incrementAndGet() == 1) {
						throw new IllegalStateException("cleanup failed");
					}
				});

		clock.advance(Duration.ofMinutes(1));
		assertEquals(1, coordinator.expireTasks(clock.instant()));
		assertEquals(1, attempts.get());
		assertFalse(coordinator.isBusy("interview_1"));
		assertTrue(coordinator.hasPendingTimeoutCallback("interview_1"));

		assertEquals(0, coordinator.expireTasks(clock.instant()));
		assertEquals(2, attempts.get());
		assertFalse(coordinator.hasPendingTimeoutCallback("interview_1"));
		assertFalse(coordinator.isBusy("interview_1"));
	}

	@Test
	void timeoutCallbackErrorIsRetriedWithoutBlockingOtherInterviews() {
		MutableClock clock = new MutableClock(Instant.parse("2030-01-01T00:00:00Z"));
		List<Runnable> drains = new CopyOnWriteArrayList<>();
		InterviewExecutionCoordinator coordinator = new InterviewExecutionCoordinator(
				drains::add, clock, () -> { });
		AtomicInteger errorAttempts = new AtomicInteger();
		AtomicInteger otherCallbacks = new AtomicInteger();
		coordinator.executeUntil(
				"interview_1",
				InterviewTaskType.PROCESSING,
				clock.instant(),
				Duration.ofMinutes(1),
					lease -> { },
				() -> {
					if (errorAttempts.incrementAndGet() == 1) {
						throw new AssertionError("cleanup error");
					}
				});
		coordinator.executeUntil(
				"interview_2",
				InterviewTaskType.FINALIZING,
				clock.instant(),
				Duration.ofMinutes(1),
					lease -> { },
				otherCallbacks::incrementAndGet);

		clock.advance(Duration.ofMinutes(1));
		assertEquals(2, coordinator.expireTasks(clock.instant()));
		assertEquals(1, errorAttempts.get());
		assertEquals(1, otherCallbacks.get());
		assertTrue(coordinator.hasPendingTimeoutCallback("interview_1"));
		assertFalse(coordinator.isBusy("interview_1"));
		assertFalse(coordinator.isBusy("interview_2"));

		assertEquals(0, coordinator.expireTasks(clock.instant()));
		assertEquals(2, errorAttempts.get());
		assertFalse(coordinator.hasPendingTimeoutCallback("interview_1"));
	}

	@Test
	void idleMaintenanceFencesConcurrentTaskAcceptanceWithoutUsingProviderLock() throws Exception {
		List<Runnable> drains = new CopyOnWriteArrayList<>();
		InterviewExecutionCoordinator coordinator = new InterviewExecutionCoordinator(drains::add);
		ExecutorService callers = Executors.newFixedThreadPool(2);
		CountDownLatch maintenanceStarted = new CountDownLatch(1);
		CountDownLatch releaseMaintenance = new CountDownLatch(1);
		try {
			Future<Boolean> maintenance = callers.submit(() -> coordinator.runIfIdle(
					"interview_1",
					() -> {
						maintenanceStarted.countDown();
						await(releaseMaintenance);
					}));
			assertTrue(maintenanceStarted.await(5, TimeUnit.SECONDS));
			Future<?> acceptance = callers.submit(() -> coordinator.execute(
					"interview_1", InterviewTaskType.PROCESSING, () -> { }));

			assertThrows(TimeoutException.class, () -> acceptance.get(100, TimeUnit.MILLISECONDS));
			assertTrue(drains.isEmpty());
			releaseMaintenance.countDown();
			assertTrue(maintenance.get(5, TimeUnit.SECONDS));
			acceptance.get(5, TimeUnit.SECONDS);
			assertEquals(1, drains.size());
		} finally {
			releaseMaintenance.countDown();
			callers.shutdownNow();
		}
	}

	private static void trackConcurrency(
			AtomicInteger concurrentTasks,
			AtomicInteger maximumConcurrency) {
		int current = concurrentTasks.incrementAndGet();
		maximumConcurrency.accumulateAndGet(current, Math::max);
	}

	private static void await(CountDownLatch latch) {
		try {
			if (!latch.await(5, TimeUnit.SECONDS)) {
				throw new IllegalStateException("timed out waiting for coordinator test");
			}
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("coordinator test interrupted", exception);
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
