package com.unispeaking.service.achievement;

import com.unispeaking.domain.dto.achievement.AchievementCollectionResponse;
import java.util.UUID;

public interface AchievementService {

	AchievementCollectionResponse synchronize(UUID userId);
}
