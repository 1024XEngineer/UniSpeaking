package com.unispeaking.component.session;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class InterviewExecutionCoordinator {

	private final Executor executor;
	private final Runnable stateReadBarrier;
	private final ConcurrentHashMap<String, Slot> slots = new ConcurrentHashMap<>();

	@Autowired
	public InterviewExecutionCoordinator(
			@Qualifier("interviewTaskExecutor") Executor executor) {
		this(executor, () -> { });
	}

	InterviewExecutionCoordinator(Executor executor, Runnable stateReadBarrier) {
		this.executor = Objects.requireNonNull(executor, "executor");
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
		String requiredId = requireInterviewId(interviewId);
		InterviewTaskType requiredType = Objects.requireNonNull(taskType, "taskType");
		Objects.requireNonNull(task, "task");
		Slot slot = retain(requiredId);
		QueuedTask queuedTask = new QueuedTask(requiredType, task);
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

	private void drain(String interviewId, Slot slot) {
		Throwable firstFailure = null;
		QueuedTask task;
		while ((task = slot.nextTask()) != null) {
			slot.lock();
			try {
				task.action().run();
			} catch (RuntimeException | Error failure) {
				if (firstFailure == null) {
					firstFailure = failure;
				}
			} finally {
				slot.unlock();
				slot.completeTask(task.type());
				release(interviewId, slot);
			}
		}
		if (firstFailure instanceof Error error) {
			throw error;
		}
		if (firstFailure instanceof RuntimeException exception) {
			throw exception;
		}
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

	private static final class Slot {

		private final ReentrantLock lock = new ReentrantLock(true);
		private final Deque<QueuedTask> tasks = new ArrayDeque<>();
		private int references;
		private int processingTasks;
		private int finalizingTasks;
		private boolean drainScheduled;
		private boolean scheduling;

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
			waitForSchedulingAttempt();
			tasks.addLast(task);
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
			drainScheduled = false;
			scheduling = false;
			notifyAll();
		}

		private synchronized void completeTask(InterviewTaskType taskType) {
			decrementTaskCount(taskType);
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

		private synchronized InterviewExecutionState state() {
			return new InterviewExecutionState(processingTasks, finalizingTasks);
		}
	}

	private record QueuedTask(InterviewTaskType type, Runnable action) { }
}
