package com.unispeaking.domain.dto.profile;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ProfileOverviewResponse(Account account, Calendar calendar) {
	public record Account(
			UUID userId,
			String email,
			String nickname,
			String displayName,
			String avatarUrl,
			Instant avatarUrlExpiresAt) {
	}

	public record Calendar(
			String month,
			List<LocalDate> checkedDates,
			boolean checkedInToday) {
		public Calendar {
			checkedDates = List.copyOf(checkedDates);
		}
	}
}
