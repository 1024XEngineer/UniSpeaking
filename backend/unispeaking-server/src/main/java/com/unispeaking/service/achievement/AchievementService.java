package com.unispeaking.service.achievement;

import com.unispeaking.domain.dto.achievement.AchievementAcknowledgeRequest;
import com.unispeaking.domain.dto.achievement.AchievementAcknowledgeResponse;
import com.unispeaking.domain.dto.achievement.AchievementOverviewResponse;
import com.unispeaking.domain.dto.achievement.AchievementSyncResponse;
import java.util.UUID;

public interface AchievementService {

	AchievementOverviewResponse getOverview(UUID userId);

	AchievementSyncResponse synchronize(UUID userId);

	AchievementAcknowledgeResponse acknowledge(
			UUID userId,
			String achievementId,
			AchievementAcknowledgeRequest request);
}
