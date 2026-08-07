package com.unispeaking.service.achievement;

import com.unispeaking.domain.dto.achievement.AchievementAcknowledgeRequest;
import com.unispeaking.domain.dto.achievement.AchievementAcknowledgeResponse;
import com.unispeaking.domain.dto.achievement.AchievementOverviewResponse;
import com.unispeaking.domain.dto.achievement.AchievementSyncResponse;
import java.util.UUID;

public interface AchievementService {

	/** Returns the user's current achievement progress and unlocked items. */
	AchievementOverviewResponse getOverview(UUID userId);

	/** Recalculates achievement progress from the user's latest activity. */
	AchievementSyncResponse synchronize(UUID userId);

	/** Marks an unlocked achievement as acknowledged by the user. */
	AchievementAcknowledgeResponse acknowledge(
			UUID userId,
			String achievementId,
			AchievementAcknowledgeRequest request);
}
