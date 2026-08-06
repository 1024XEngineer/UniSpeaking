package com.unispeaking.domain.dto.profile;

import com.unispeaking.domain.po.profile.WeeklyLearningGoals;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateWeeklyLearningGoalsRequest(
		@NotNull(message = "不能为空")
		@Min(value = 1, message = "必须大于 0")
		@Max(
				value = WeeklyLearningGoals.MAX_DURATION_TARGET_MINUTES,
				message = "不能超过 1260 分钟")
		Integer durationTargetMinutes,
		@NotNull(message = "不能为空")
		@Min(value = 1, message = "必须大于 0")
		@Max(
				value = WeeklyLearningGoals.MAX_TRAINING_COUNT_TARGET,
				message = "不能超过 70 次")
		Integer trainingCountTarget) {

	public WeeklyLearningGoals toDomain() {
		return new WeeklyLearningGoals(
				durationTargetMinutes,
				trainingCountTarget);
	}
}
