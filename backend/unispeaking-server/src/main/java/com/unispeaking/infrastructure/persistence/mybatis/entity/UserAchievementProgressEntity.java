package com.unispeaking.infrastructure.persistence.mybatis.entity;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UserAchievementProgressEntity {

	private UUID userId;
	private UUID achievementId;
	private Long progressValue;
	private OffsetDateTime unlockedAt;
	private OffsetDateTime createdAt;
	private OffsetDateTime updatedAt;
}
