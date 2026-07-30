package com.unispeaking.service.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.unispeaking.domain.dto.achievement.AchievementCollectionResponse;
import com.unispeaking.service.achievement.AchievementService;
import com.unispeaking.service.profile.impl.ProfileOverviewServiceImpl;
import com.unispeaking.service.profile.query.LearningAssetCountPort;
import com.unispeaking.service.profile.query.LearningStatisticsQueryPort;
import com.unispeaking.service.profile.query.LearningStatisticsQueryPort.LearningCalendarDay;
import com.unispeaking.service.profile.query.LearningStatisticsQueryPort.LearningSummary;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProfileOverviewServiceImplTest {

	@Mock
	private LearningStatisticsQueryPort statisticsPort;
	@Mock
	private LearningAssetCountPort assetCountPort;
	@Mock
	private AchievementService achievementService;

	@Test
	void composesCurrentMonthForSameUserAndIncludesAssetOnlyDates() {
		UUID userId = UUID.fromString("22222222-2222-4222-8222-222222222222");
		YearMonth july = YearMonth.of(2026, 7);
		ZoneId zoneId = ZoneId.of("Asia/Shanghai");
		when(statisticsPort.summary(userId, zoneId))
				.thenReturn(new LearningSummary(183, 7));
		when(statisticsPort.calendar(userId, july, zoneId))
				.thenReturn(List.of(new LearningCalendarDay(
						LocalDate.of(2026, 7, 1),
						28,
						2)));
		when(assetCountPort.countSavedAssets(userId)).thenReturn(12L);
		when(assetCountPort.countSavedAssetsByDate(userId, july, zoneId))
				.thenReturn(Map.of(LocalDate.of(2026, 7, 2), 4L));
		when(achievementService.synchronize(userId))
				.thenReturn(AchievementCollectionResponse.empty());
		var service = new ProfileOverviewServiceImpl(
				statisticsPort,
				assetCountPort,
				achievementService,
				Clock.fixed(Instant.parse("2026-07-30T04:00:00Z"), ZoneOffset.UTC));

		var response = service.getOverview(userId, null);

		assertEquals(183, response.summary().weeklyMinutes());
		assertEquals(12, response.summary().savedAssetCount());
		assertEquals(7, response.summary().continuousLearningDays());
		assertEquals(july, response.calendar().yearMonth());
		assertEquals(2, response.calendar().days().size());
		assertEquals(0, response.calendar().days().get(0).savedAssetCount());
		assertEquals(LocalDate.of(2026, 7, 2), response.calendar().days().get(1).date());
		assertEquals(4, response.calendar().days().get(1).savedAssetCount());
	}
}
