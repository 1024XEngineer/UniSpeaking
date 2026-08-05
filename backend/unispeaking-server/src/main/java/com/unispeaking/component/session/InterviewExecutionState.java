package com.unispeaking.component.session;

public record InterviewExecutionState(
		int processingTasks,
		int finalizingTasks) {

	public InterviewExecutionState {
		if (processingTasks < 0 || finalizingTasks < 0) {
			throw new IllegalArgumentException("task counts must not be negative");
		}
	}

	public boolean busy() {
		return processingTasks > 0 || finalizingTasks > 0;
	}

	public static InterviewExecutionState idle() {
		return new InterviewExecutionState(0, 0);
	}
}
