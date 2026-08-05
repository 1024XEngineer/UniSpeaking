package com.unispeaking.domain.po.profile;

public record WeeklyLearningGoals(
		int durationTargetMinutes,
		int trainingCountTarget) {

	public static final int DEFAULT_DURATION_TARGET_MINUTES = 120;
	public static final int DEFAULT_TRAINING_COUNT_TARGET = 5;
	public static final int MAX_DURATION_TARGET_MINUTES = 1260;
	public static final int MAX_TRAINING_COUNT_TARGET = 70;

	public static WeeklyLearningGoals defaults() {
		return new WeeklyLearningGoals(
				DEFAULT_DURATION_TARGET_MINUTES,
				DEFAULT_TRAINING_COUNT_TARGET);
	}

	public static WeeklyLearningGoals fromStored(
			Integer durationTargetMinutes,
			Integer trainingCountTarget) {
		return new WeeklyLearningGoals(
				normalize(
						durationTargetMinutes,
						DEFAULT_DURATION_TARGET_MINUTES,
						MAX_DURATION_TARGET_MINUTES),
				normalize(
						trainingCountTarget,
						DEFAULT_TRAINING_COUNT_TARGET,
						MAX_TRAINING_COUNT_TARGET));
	}

	private static int normalize(Integer value, int defaultValue, int maximum) {
		return value == null || value < 1 || value > maximum
				? defaultValue
				: value;
	}
}
