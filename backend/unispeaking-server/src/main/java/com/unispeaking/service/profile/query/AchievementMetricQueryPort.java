package com.unispeaking.service.profile.query;

import com.unispeaking.domain.vo.achievement.AchievementMetricKey;
import java.util.UUID;

public interface AchievementMetricQueryPort {

	long metricValue(UUID userId, AchievementMetricKey metricKey);
}
