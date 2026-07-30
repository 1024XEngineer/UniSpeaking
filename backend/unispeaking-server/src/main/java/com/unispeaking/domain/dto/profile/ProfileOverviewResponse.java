package com.unispeaking.domain.dto.profile;

import com.unispeaking.domain.dto.achievement.AchievementCollectionResponse;

public record ProfileOverviewResponse(
		LearningSummaryResponse summary,
		LearningCalendarResponse calendar,
		AchievementCollectionResponse achievements) {
}
