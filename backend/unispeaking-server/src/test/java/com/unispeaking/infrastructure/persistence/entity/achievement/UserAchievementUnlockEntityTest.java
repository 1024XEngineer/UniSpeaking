package com.unispeaking.infrastructure.persistence.entity.achievement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserAchievementUnlockEntityTest {

	@Test
	void retainsCompositeKeyTimestampsAndNullableAcknowledgement() {
		UUID userId = UUID.fromString("11111111-1111-4111-8111-111111111111");
		OffsetDateTime unlockedAt = OffsetDateTime.parse("2026-08-04T02:00:00Z");
		UserAchievementUnlockEntity entity = new UserAchievementUnlockEntity();

		entity.setUserId(userId);
		entity.setAchievementId("conversation-1");
		entity.setUnlockedAt(unlockedAt);
		entity.setAcknowledgedAt(null);
		entity.setCreatedAt(unlockedAt);
		entity.setUpdatedAt(unlockedAt.plusSeconds(1));

		assertEquals(userId, entity.getUserId());
		assertEquals("conversation-1", entity.getAchievementId());
		assertEquals(unlockedAt, entity.getUnlockedAt());
		assertNull(entity.getAcknowledgedAt());
		assertEquals(unlockedAt, entity.getCreatedAt());
		assertEquals(unlockedAt.plusSeconds(1), entity.getUpdatedAt());
	}

	@Test
	void supportsAcknowledgementLifecycleWithoutChangingTheCompositeKey() {
		UUID userId = UUID.randomUUID();
		OffsetDateTime unlockedAt = OffsetDateTime.parse("2026-08-04T02:00:00+08:00");
		UserAchievementUnlockEntity entity = new UserAchievementUnlockEntity();

		assertNull(entity.getUserId());
		assertNull(entity.getAchievementId());
		assertNull(entity.getAcknowledgedAt());
		entity.setUserId(userId);
		entity.setAchievementId("daily-streak-7");
		entity.setUnlockedAt(unlockedAt);
		entity.setAcknowledgedAt(unlockedAt.plusMinutes(2));
		entity.setUpdatedAt(unlockedAt.plusMinutes(2));

		assertEquals(userId, entity.getUserId());
		assertEquals("daily-streak-7", entity.getAchievementId());
		assertEquals(unlockedAt.plusMinutes(2), entity.getAcknowledgedAt());
		assertEquals(unlockedAt.plusMinutes(2), entity.getUpdatedAt());
	}
}
