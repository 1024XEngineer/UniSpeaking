package com.unispeaking.repository;

import com.unispeaking.domain.po.achievement.AchievementDefinition;
import com.unispeaking.domain.po.achievement.UserAchievementProgress;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AchievementRepository {

	List<AchievementDefinition> findActiveDefinitions();

	Optional<UserAchievementProgress> findProgress(UUID userId, UUID achievementId);

	UserAchievementProgress upsertProgress(UserAchievementProgress progress);
}
