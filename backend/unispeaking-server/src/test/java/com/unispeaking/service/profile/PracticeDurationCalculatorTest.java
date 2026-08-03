package com.unispeaking.service.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.unispeaking.domain.po.session.PracticeSessionRecord;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.domain.vo.session.SessionStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PracticeDurationCalculatorTest {

	private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");
	private final PracticeDurationCalculator calculator =
			new PracticeDurationCalculator();

	@Test
	void excludesSessionsShorterThanThreeMinutes() {
		var result = calculate(List.of(
				record("2026-08-03T01:00:00Z", "2026-08-03T01:02:59Z"),
				record("2026-08-03T02:00:00Z", "2026-08-03T02:03:00Z")));

		assertEquals(180, result.weeklyPracticeSeconds());
		assertEquals(180, result.lastSevenDays().getLast().practiceSeconds());
	}

	@Test
	void splitsEligibleSessionAcrossLocalCalendarDays() {
		var result = calculate(List.of(
				record("2026-08-02T15:58:00Z", "2026-08-02T16:03:00Z")));

		assertEquals(120, result.lastSevenDays().get(5).practiceSeconds());
		assertEquals(180, result.lastSevenDays().get(6).practiceSeconds());
	}

	private com.unispeaking.domain.dto.profile.ProfileOverviewResponse
			.PracticeStatistics calculate(List<PracticeSessionRecord> records) {
		return calculator.calculate(
				records,
				LocalDate.of(2026, 8, 3),
				Instant.parse("2026-08-03T12:00:00Z"),
				ZONE_ID,
				4,
				2);
	}

	private PracticeSessionRecord record(String start, String end) {
		return new PracticeSessionRecord(
				"freechat_" + start,
				UUID.randomUUID(),
				"freechat_scene",
				SceneType.FREE_CHAT,
				SessionStatus.COMPLETED,
				Instant.parse(start),
				Instant.parse(end));
	}
}
