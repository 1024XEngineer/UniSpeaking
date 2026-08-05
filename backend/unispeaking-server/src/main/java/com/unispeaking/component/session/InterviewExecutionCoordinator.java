package com.unispeaking.component.session;

import com.unispeaking.domain.po.session.InterviewSubmission;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class InterviewExecutionCoordinator {

	private static final Logger LOGGER = LoggerFactory.getLogger(
			InterviewExecutionCoordinator.class);

	private final Executor executor;
	private final Clock clock;
	private final Runnable stateReadBarrier;
	private final ConcurrentHashMap<String, Slot> slots = new ConcurrentHashMap<>();
	private final ConcurrentLinkedQueue<PendingTimeoutCallback> pendingTimeoutCallbacks =
			new ConcurrentLinkedQueue<>();

	@Autowired
	public InterviewExecutionCoordinator(
			@Qualifier("interviewTaskExecutor") Executor executor,
			@Qualifier("interviewClock") Clock clock) {
		this(executor, clock, () -> { });
	}

	InterviewExecutionCoordinator(Executor executor, Runnable stateReadBarrier) {
		this(executor, Clock.systemUTC(), stateReadBarrier);
	}

	InterviewExecutionCoordinator(Executor executor) {
		this(executor, Clock.systemUTC(), () -> { });
	}

	InterviewExecutionCoordinator(
			Executor executor,
			Clock clock,
			Runnable stateReadBarrier) {
		this.executor = Objects.requireNonNull(executor, "executor");
		this.clock = Objects.requireNonNull(clock, "clock");
		this.stateReadBarrier = Objects.requireNonNull(stateReadBarrier, "stateReadBarrier");
	}

	public <T> T withLock(String interviewId, Supplier<T> action) {
		String requiredId = requireInterviewId(interviewId);
		Objects.requireNonNull(action, "action");
		Slot slot = retain(requiredId);
		slot.lock();
		try {
			return action.get();
		} finally {
			slot.unlock();
			release(requiredId, slot);
		}
	}

	public void withLock(String interviewId, Runnable action) {
		Objects.requireNonNull(action, "action");
		withLock(interviewId, () -> {
			action.run();
			return null;
		});
	}

	public void execute(
			String interviewId,
			InterviewTaskType taskType,
			Runnable task) {
		executeUntil(
				interviewId,
				taskType,
				clock.instant(),
				InterviewRuntimePolicy.MAXIMUM_TASK_TIMEOUT,
				lease -> task.run(),
				() -> { });
	}

	public void executeSubmission(
			String interviewId,
			InterviewSubmission submission,
			GuardedTask task,
			Runnable temporaryDataCleanup) {
		InterviewSubmission requiredSubmission = Objects.requireNonNull(
				submission, "submission");
		Runnable requiredCleanup = Objects.requireNonNull(
				temporaryDataCleanup, "temporaryDataCleanup");
		executeUntil(
				interviewId,
				InterviewTaskType.PROCESSING,
				requiredSubmission.acceptedAt(),
				InterviewRuntimePolicy.MAXIMUM_TASK_TIMEOUT,
				task,
				() -> {
					requiredSubmission.markTimedOut(
							true,
							"INTERVIEW_SUBMISSION_TIMEOUT",
							"Interview submission exceeded its processing deadline",
							clock.instant());
				},
				requiredCleanup);
	}

	public void executeFinalizer(
			String interviewId,
			Instant startedAt,
			GuardedTask task,
			Runnable timeoutHandler) {
		executeUntil(
				interviewId,
				InterviewTaskType.FINALIZING,
				startedAt,
				InterviewRuntimePolicy.MAXIMUM_TASK_TIMEOUT,
				task,
				timeoutHandler);
	}

	public void executeUntil(
			String interviewId,
			InterviewTaskType taskType,
			Instant startedAt,
			Duration requestedTimeout,
			GuardedTask task,
			Runnable timeoutHandler) {
		executeUntil(
				interviewId,
				taskType,
				startedAt,
				requestedTimeout,
				task,
				timeoutHandler,
				() -> { });
	}

	private void executeUntil(
			String interviewId,
			InterviewTaskType taskType,
			Instant startedAt,
			Duration requestedTimeout,
			GuardedTask task,
			Runnable timeoutHandler,
			Runnable timeoutCleanup) {
		String requiredId = requireInterviewId(interviewId);
		InterviewTaskType requiredType = Objects.requireNonNull(taskType, "taskType");
		Instant deadline = deadline(startedAt, requestedTimeout);
		Objects.requireNonNull(task, "task");
		Objects.requireNonNull(timeoutHandler, "timeoutHandler");
		Objects.requireNonNull(timeoutCleanup, "timeoutCleanup");
		Slot slot = retain(requiredId);
		QueuedTask queuedTask = new QueuedTask(
				requiredType, deadline, task, timeoutHandler, timeoutCleanup);
		boolean scheduleDrain = slot.enqueue(queuedTask);
		if (!scheduleDrain) {
			return;
		}
		try {
			executor.execute(() -> drain(requiredId, slot));
			slot.completeScheduling();
		} catch (RuntimeException | Error exception) {
			slot.rejectScheduling(queuedTask);
			release(requiredId, slot);
			throw exception;
		}
	}

	public int expireTasks(Instant now) {
		Instant requiredNow = Objects.requireNonNull(now, "now");
		retryPendingTimeoutCallbacks();
		List<ExpiredTask> expired = new ArrayList<>();
		for (var entry : slots.entrySet()) {
			Slot slot = entry.getValue();
			for (QueuedTask task : slot.expire(requiredNow)) {
				expired.add(new ExpiredTask(entry.getKey(), slot, task));
			}
		}
		for (ExpiredTask task : expired) {
			if (task.queued()) {
				release(task.interviewId(), task.slot());
			}
			runTimeoutCallback(
					task.interviewId(), task.task().type(), task.task().timeoutHandler());
			if (task.queued()) {
				runTimeoutCallback(
						task.interviewId(), task.task().type(), task.task().timeoutCleanup());
			}
		}
		return expired.size();
	}

	public boolean hasPendingTimeoutCallback(String interviewId) {
		String requiredId = requireInterviewId(interviewId);
		return pendingTimeoutCallbacks.stream()
				.anyMatch(callback -> callback.interviewId().equals(requiredId));
	}

	public InterviewExecutionState state(String interviewId) {
		String requiredId = requireInterviewId(interviewId);
		Slot slot = retain(requiredId);
		try {
			stateReadBarrier.run();
			return slot.state();
		} finally {
			release(requiredId, slot);
		}
	}

	public boolean isBusy(String interviewId) {
		return state(interviewId).busy();
	}

	public boolean runIfIdle(String interviewId, Runnable maintenance) {
		String requiredId = requireInterviewId(interviewId);
		Objects.requireNonNull(maintenance, "maintenance");
		Slot slot = retain(requiredId);
		if (!slot.beginMaintenance()) {
			release(requiredId, slot);
			return false;
		}
		try {
			maintenance.run();
			return true;
		} finally {
			slot.endMaintenance();
			release(requiredId, slot);
		}
	}

	private void drain(String interviewId, Slot slot) {
		Throwable firstFailure = null;
		QueuedTask task;
		while ((task = slot.nextTask()) != null) {
			slot.lock();
			try {
				if (task.shouldRun()) {
					task.action().run(new TaskLease(task));
				}
			} catch (RuntimeException | Error failure) {
				if (firstFailure == null) {
					firstFailure = failure;
				}
			} finally {
				slot.unlock();
				Runnable deferredCleanup = slot.completeTask(task);
				release(interviewId, slot);
				if (deferredCleanup != null) {
					runTimeoutCallback(interviewId, task.type(), deferredCleanup);
				}
			}
		}
		if (firstFailure instanceof Error error) {
			throw error;
		}
		if (firstFailure instanceof RuntimeException exception) {
			throw exception;
		}
	}

	private void runTimeoutCallback(
			String interviewId,
			InterviewTaskType taskType,
			Runnable callback) {
		try {
			callback.run();
		} catch (RuntimeException | Error exception) {
			PendingTimeoutCallback pending = new PendingTimeoutCallback(
					interviewId, taskType, callback);
			pendingTimeoutCallbacks.offer(pending);
			logTimeoutCallbackFailure(pending, exception);
		}
	}

	private void retryPendingTimeoutCallbacks() {
		for (PendingTimeoutCallback pending : List.copyOf(pendingTimeoutCallbacks)) {
			if (!pending.tryStart()) {
				continue;
			}
			boolean completed = false;
			try {
				pending.callback().run();
				completed = true;
			} catch (RuntimeException | Error exception) {
				logTimeoutCallbackFailure(pending, exception);
			} finally {
				if (completed) {
					pendingTimeoutCallbacks.remove(pending);
				} else {
					pending.finishAttempt();
				}
			}
		}
	}

	private static void logTimeoutCallbackFailure(
			PendingTimeoutCallback pending,
			Throwable exception) {
		LOGGER.warn(
				"Interview timeout callback failed interviewId={} taskType={}",
				pending.interviewId(),
				pending.taskType(),
				exception);
	}

	private Slot retain(String interviewId) {
		return slots.compute(interviewId, (key, existing) -> {
			Slot slot = existing == null ? new Slot() : existing;
			slot.retain();
			return slot;
		});
	}

	private void release(String interviewId, Slot expected) {
		slots.computeIfPresent(interviewId, (key, existing) -> {
			if (existing != expected) {
				return existing;
			}
			return existing.release() ? null : existing;
		});
	}

	private static String requireInterviewId(String interviewId) {
		if (interviewId == null || interviewId.isBlank()) {
			throw new IllegalArgumentException("interviewId must not be blank");
		}
		return interviewId.trim();
	}

	@FunctionalInterface
	public interface GuardedTask {
		void run(TaskLease lease);
	}

	public static final class TaskLease {

		private final QueuedTask task;

		private TaskLease(QueuedTask task) {
			this.task = task;
		}

		public boolean isActive() {
			return task.isActive();
		}

		public boolean commitIfActive(Runnable commit) {
			return task.commitIfActive(commit);
		}
	}

	private static Instant deadline(Instant startedAt, Duration requestedTimeout) {
		Instant requiredStart = Objects.requireNonNull(startedAt, "startedAt");
		Duration requiredTimeout = Objects.requireNonNull(requestedTimeout, "requestedTimeout");
		if (requiredTimeout.isZero() || requiredTimeout.isNegative()) {
			throw new IllegalArgumentException("requestedTimeout must be positive");
		}
		Duration boundedTimeout = requiredTimeout.compareTo(
				InterviewRuntimePolicy.MAXIMUM_TASK_TIMEOUT) > 0
						? InterviewRuntimePolicy.MAXIMUM_TASK_TIMEOUT
						: requiredTimeout;
		return requiredStart.plus(boundedTimeout);
	}

	private static final class Slot {

		private final ReentrantLock lock = new ReentrantLock(true);
		private final Deque<QueuedTask> tasks = new ArrayDeque<>();
		private final Set<QueuedTask> trackedTasks = new HashSet<>();
		private int references;
		private int processingTasks;
		private int finalizingTasks;
		private int runningTasks;
		private boolean drainScheduled;
		private boolean scheduling;
		private boolean maintenance;

		private void lock() {
			lock.lock();
		}

		private void unlock() {
			lock.unlock();
		}

		private synchronized void retain() {
			references++;
		}

		private synchronized boolean release() {
			if (references < 1) {
				throw new IllegalStateException("interview execution slot is not retained");
			}
			return --references == 0;
		}

		private synchronized boolean enqueue(QueuedTask task) {
			waitForAdmission();
			tasks.addLast(task);
			trackedTasks.add(task);
			if (task.type() == InterviewTaskType.PROCESSING) {
				processingTasks++;
			} else {
				finalizingTasks++;
			}
			if (drainScheduled) {
				return false;
			}
			drainScheduled = true;
			scheduling = true;
			return true;
		}

		private synchronized QueuedTask nextTask() {
			QueuedTask task = tasks.pollFirst();
			if (task == null) {
				drainScheduled = false;
			} else {
				task.markStarted();
				runningTasks++;
			}
			return task;
		}

		private synchronized void completeScheduling() {
			scheduling = false;
			notifyAll();
		}

		private synchronized void rejectScheduling(QueuedTask expected) {
			if (!tasks.removeFirstOccurrence(expected)) {
				throw new IllegalStateException("rejected interview task is not queued");
			}
			decrementTaskCount(expected.type());
			trackedTasks.remove(expected);
			drainScheduled = false;
			scheduling = false;
			notifyAll();
		}

		private synchronized Runnable completeTask(QueuedTask task) {
			if (task.completeOccupancy()) {
				decrementTaskCount(task.type());
			}
			runningTasks--;
			trackedTasks.remove(task);
			return task.claimDeferredTimeoutCleanup();
		}

		private synchronized List<QueuedTask> expire(Instant now) {
			waitForSchedulingAttempt();
			List<QueuedTask> expired = new ArrayList<>();
			for (QueuedTask task : List.copyOf(trackedTasks)) {
				if (!task.deadline().isAfter(now) && task.expire()) {
					decrementTaskCount(task.type());
					task.queued(tasks.remove(task));
					if (task.queued()) {
						trackedTasks.remove(task);
					}
					expired.add(task);
				}
			}
			return expired;
		}

		private synchronized boolean beginMaintenance() {
			waitForSchedulingAttempt();
			if (processingTasks > 0
					|| finalizingTasks > 0
					|| runningTasks > 0
					|| maintenance) {
				return false;
			}
			maintenance = true;
			return true;
		}

		private synchronized void endMaintenance() {
			if (!maintenance) {
				throw new IllegalStateException("interview maintenance is not active");
			}
			maintenance = false;
			notifyAll();
		}

		private void decrementTaskCount(InterviewTaskType taskType) {
			if (taskType == InterviewTaskType.PROCESSING) {
				processingTasks--;
			} else {
				finalizingTasks--;
			}
		}

		private void waitForSchedulingAttempt() {
			boolean interrupted = false;
			while (scheduling) {
				try {
					wait();
				} catch (InterruptedException exception) {
					interrupted = true;
				}
			}
			if (interrupted) {
				Thread.currentThread().interrupt();
			}
		}

		private void waitForAdmission() {
			boolean interrupted = false;
			while (scheduling || maintenance) {
				try {
					wait();
				} catch (InterruptedException exception) {
					interrupted = true;
				}
			}
			if (interrupted) {
				Thread.currentThread().interrupt();
			}
		}

		private synchronized InterviewExecutionState state() {
			return new InterviewExecutionState(processingTasks, finalizingTasks);
		}
	}

	private static final class QueuedTask {

		private final InterviewTaskType type;
		private final Instant deadline;
		private final GuardedTask action;
		private final Runnable timeoutHandler;
		private final Runnable timeoutCleanup;
		private boolean occupancyActive = true;
		private boolean queued;
		private boolean started;
		private boolean committed;
		private boolean expired;
		private boolean timeoutCleanupClaimed;

		private QueuedTask(
				InterviewTaskType type,
				Instant deadline,
				GuardedTask action,
				Runnable timeoutHandler,
				Runnable timeoutCleanup) {
			this.type = type;
			this.deadline = deadline;
			this.action = action;
			this.timeoutHandler = timeoutHandler;
			this.timeoutCleanup = timeoutCleanup;
		}

		private InterviewTaskType type() { return type; }
		private Instant deadline() { return deadline; }
		private GuardedTask action() { return action; }
		private Runnable timeoutHandler() { return timeoutHandler; }
		private Runnable timeoutCleanup() { return timeoutCleanup; }

		private synchronized void markStarted() { started = true; }

		private synchronized boolean expire() {
			if (!occupancyActive || committed) {
				return false;
			}
			occupancyActive = false;
			expired = true;
			return true;
		}

		private synchronized boolean completeOccupancy() {
			if (!occupancyActive) {
				return false;
			}
			occupancyActive = false;
			return true;
		}

		private synchronized boolean isActive() {
			return occupancyActive && !committed;
		}

		private synchronized boolean commitIfActive(Runnable commit) {
			Objects.requireNonNull(commit, "commit");
			if (!occupancyActive || committed) {
				return false;
			}
			commit.run();
			committed = true;
			return true;
		}

		private synchronized Runnable claimDeferredTimeoutCleanup() {
			if (!expired || !started || timeoutCleanupClaimed) {
				return null;
			}
			timeoutCleanupClaimed = true;
			return timeoutCleanup;
		}

		private synchronized boolean shouldRun() { return occupancyActive; }

		private synchronized void queued(boolean value) { queued = value; }
		private synchronized boolean queued() { return queued; }
	}

	private record ExpiredTask(
			String interviewId,
			Slot slot,
			QueuedTask task) {

		private boolean queued() { return task.queued(); }
	}

	private static final class PendingTimeoutCallback {

		private final String interviewId;
		private final InterviewTaskType taskType;
		private final Runnable callback;
		private final AtomicBoolean executing = new AtomicBoolean();

		private PendingTimeoutCallback(
				String interviewId,
				InterviewTaskType taskType,
				Runnable callback) {
			this.interviewId = interviewId;
			this.taskType = taskType;
			this.callback = callback;
		}

		private String interviewId() { return interviewId; }
		private InterviewTaskType taskType() { return taskType; }
		private Runnable callback() { return callback; }
		private boolean tryStart() { return executing.compareAndSet(false, true); }
		private void finishAttempt() { executing.set(false); }
	}
}
