package com.unispeaking.service.achievement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.unispeaking.domain.po.achievement.AchievementDefinition;
import com.unispeaking.domain.po.achievement.UserAchievementProgress;
import com.unispeaking.domain.vo.achievement.AchievementMetricKey;
import com.unispeaking.repository.AchievementRepository;
import com.unispeaking.service.achievement.impl.AchievementServiceImpl;
import com.unispeaking.service.profile.query.AchievementMetricQueryPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AchievementServiceImplTest {

	private static final UUID USER_ID =
			UUID.fromString("22222222-2222-4222-8222-222222222222");
	private static final Instant NOW = Instant.parse("2026-07-30T04:00:00Z");

	@Mock
	private AchievementRepository repository;
	@Mock
	private AchievementMetricQueryPort metricQueryPort;
	@Captor
	private ArgumentCaptor<UserAchievementProgress> progressCaptor;

	@Test
	void capsProgressUnlocksNewAchievementAndPreservesExistingUnlockTime() {
		UUID streakId = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
		UUID firstConversationId =
				UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
		Instant originalUnlockTime = Instant.parse("2026-07-20T01:00:00Z");
		AchievementDefinition streak = definition(
				streakId,
				"SEVEN_DAY_STREAK",
				AchievementMetricKey.CONTINUOUS_LEARNING_DAYS,
				7);
		AchievementDefinition firstConversation = definition(
				firstConversationId,
				"FIRST_CONVERSATION",
				AchievementMetricKey.COMPLETED_SESSION_COUNT,
				1);
		when(repository.findActiveDefinitions())
				.thenReturn(List.of(streak, firstConversation));
		when(repository.findProgress(USER_ID, streakId))
				.thenReturn(Optional.of(new UserAchievementProgress(
						USER_ID,
						streakId,
						7,
						originalUnlockTime,
						originalUnlockTime,
						originalUnlockTime)));
		when(repository.findProgress(USER_ID, firstConversationId))
				.thenReturn(Optional.empty());
		when(metricQueryPort.metricValue(
				USER_ID,
				AchievementMetricKey.CONTINUOUS_LEARNING_DAYS))
				.thenReturn(20L);
		when(metricQueryPort.metricValue(
				USER_ID,
				AchievementMetricKey.COMPLETED_SESSION_COUNT))
				.thenReturn(1L);
		when(repository.upsertProgress(any(UserAchievementProgress.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		var response = service().synchronize(USER_ID);

		org.mockito.Mockito.verify(repository, org.mockito.Mockito.times(2))
				.upsertProgress(progressCaptor.capture());
		List<UserAchievementProgress> saved = progressCaptor.getAllValues();
		assertEquals(7, saved.get(0).progressValue());
		assertEquals(originalUnlockTime, saved.get(0).unlockedAt());
		assertEquals(1, saved.get(1).progressValue());
		assertEquals(NOW, saved.get(1).unlockedAt());
		assertEquals(2, response.unlockedCount());
		assertEquals(2, response.totalCount());
	}

	private AchievementServiceImpl service() {
		return new AchievementServiceImpl(
				repository,
				metricQueryPort,
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private AchievementDefinition definition(
			UUID id,
			String code,
			AchievementMetricKey metricKey,
			long targetValue) {
		return new AchievementDefinition(
				id,
				code,
				code,
				code,
				"LEARNING",
				metricKey,
				targetValue,
				"award",
				0,
				NOW,
				NOW);
	}
}
