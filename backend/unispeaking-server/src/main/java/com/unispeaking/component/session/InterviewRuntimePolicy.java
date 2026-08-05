package com.unispeaking.component.session;

import java.time.Duration;
import java.util.Objects;

public record InterviewRuntimePolicy(
		Duration idleTimeout,
		Duration recoveryWindow,
		Duration taskTimeout,
		Duration scanInterval) {

	public static final Duration MAXIMUM_TASK_TIMEOUT = Duration.ofMinutes(10);

	public InterviewRuntimePolicy {
		idleTimeout = requirePositive(idleTimeout, "idleTimeout");
		recoveryWindow = requirePositive(recoveryWindow, "recoveryWindow");
		taskTimeout = requirePositive(taskTimeout, "taskTimeout");
		scanInterval = requirePositive(scanInterval, "scanInterval");
		if (taskTimeout.compareTo(MAXIMUM_TASK_TIMEOUT) > 0) {
			throw new IllegalArgumentException("taskTimeout must not exceed 10 minutes");
		}
		if (idleTimeout.compareTo(recoveryWindow) >= 0) {
			throw new IllegalArgumentException("idleTimeout must be shorter than recoveryWindow");
		}
	}

	public static InterviewRuntimePolicy defaults() {
		return new InterviewRuntimePolicy(
				Duration.ofMinutes(1),
				Duration.ofMinutes(10),
				MAXIMUM_TASK_TIMEOUT,
				Duration.ofMinutes(1));
	}

	private static Duration requirePositive(Duration value, String name) {
		Duration required = Objects.requireNonNull(value, name);
		if (required.isZero() || required.isNegative()) {
			throw new IllegalArgumentException(name + " must be positive");
		}
		return required;
	}
}
