package com.unispeaking.service.profile.impl;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.domain.dto.profile.ProfileOverviewResponse;
import com.unispeaking.domain.po.auth.UserAccount;
import com.unispeaking.infrastructure.config.ObjectStorageProperties;
import com.unispeaking.infrastructure.config.ProfileProperties;
import com.unispeaking.infrastructure.persistence.repository.evaluation.SessionEvaluationRepository;
import com.unispeaking.infrastructure.persistence.repository.scene.SceneRepository;
import com.unispeaking.infrastructure.persistence.repository.session.PracticeSessionRepository;
import com.unispeaking.infrastructure.persistence.repository.user.UserAccountRepository;
import com.unispeaking.provider.ObjectStorageProvider;
import com.unispeaking.service.profile.ProfileOverviewService;
import com.unispeaking.component.profile.PracticeDurationCalculator;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ProfileOverviewServiceImpl implements ProfileOverviewService {
	private final UserAccountRepository accounts;
	private final SceneRepository scenes;
	private final SessionEvaluationRepository evaluations;
	private final PracticeSessionRepository practiceSessions;
	private final ObjectStorageProvider storage;
	private final ObjectStorageProperties storageProperties;
	private final ZoneId zoneId;
	private final Clock clock;
	private final PracticeDurationCalculator durationCalculator;

	@Autowired
	public ProfileOverviewServiceImpl(
			UserAccountRepository accounts,
			SceneRepository scenes,
			SessionEvaluationRepository evaluations,
			PracticeSessionRepository practiceSessions,
			ObjectStorageProvider storage,
			ObjectStorageProperties storageProperties,
			ProfileProperties profileProperties) {
		this(
				accounts,
				scenes,
				evaluations,
				practiceSessions,
				storage,
				storageProperties,
				profileProperties.zoneId(),
				Clock.system(profileProperties.zoneId()));
	}

	ProfileOverviewServiceImpl(
			UserAccountRepository accounts,
			SceneRepository scenes,
			SessionEvaluationRepository evaluations,
			PracticeSessionRepository practiceSessions,
			ObjectStorageProvider storage,
			ObjectStorageProperties storageProperties,
			ZoneId zoneId,
			Clock clock) {
		this.accounts = accounts;
		this.scenes = scenes;
		this.evaluations = evaluations;
		this.practiceSessions = practiceSessions;
		this.storage = storage;
		this.storageProperties = storageProperties;
		this.zoneId = zoneId;
		this.clock = clock;
		this.durationCalculator = new PracticeDurationCalculator();
	}

	@Override
	public ProfileOverviewResponse getOverview(String userId, String requestedMonth) {
		UUID id = UUID.fromString(userId);
		UserAccount user = accounts.findById(id)
				.orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "用户不存在"));
		Instant now = clock.instant();
		LocalDate today = now.atZone(zoneId).toLocalDate();
		YearMonth current = YearMonth.from(today);
		YearMonth month = parseMonth(requestedMonth, current);
		if (month.isAfter(current)) {
			throw new BusinessException("PROFILE_MONTH_INVALID", "不能查看未来月份");
		}
		Instant start = month.atDay(1).atStartOfDay(zoneId).toInstant();
		Instant end = month.plusMonths(1).atDay(1).atStartOfDay(zoneId).toInstant();
		List<String> sceneIds = scenes.findAllIdsByUserId(userId);
		List<LocalDate> dates = evaluations.findCreatedAtBySceneIdsBetween(
						sceneIds,
						start.atOffset(ZoneOffset.UTC),
						end.atOffset(ZoneOffset.UTC))
				.stream()
				.map(value -> value.toInstant().atZone(zoneId).toLocalDate())
				.distinct()
				.sorted()
				.toList();
		SignedAvatar signed = signAvatar(user.avatarObjectKey());
		String displayName = displayName(user);
		LocalDate sevenDayStart = today.minusDays(6);
		LocalDate weekStart = today.with(
				TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
		Instant statisticsStart = (sevenDayStart.isBefore(weekStart)
				? sevenDayStart
				: weekStart).atStartOfDay(zoneId).toInstant();
		int consecutiveLearningDays = consecutiveLearningDays(
				evaluations.findCreatedAtBySceneIdsBetween(
						sceneIds,
						Instant.EPOCH.atOffset(ZoneOffset.UTC),
						today.plusDays(1).atStartOfDay(zoneId)
								.toInstant().atOffset(ZoneOffset.UTC)),
				today);
		ProfileOverviewResponse.PracticeStatistics statistics =
				durationCalculator.calculate(
						practiceSessions.findCompletedOverlapping(
								id, statisticsStart, now),
						today,
						now,
						zoneId,
						scenes.countActiveByUserId(userId),
						consecutiveLearningDays);
		return new ProfileOverviewResponse(
				new ProfileOverviewResponse.Account(
						user.id(), user.username(), user.nickname(), displayName,
						signed.url(), signed.expiresAt()),
				statistics,
				new ProfileOverviewResponse.Calendar(
						month.toString(), dates, dates.contains(today)));
	}

	private int consecutiveLearningDays(
			List<OffsetDateTime> reportTimes,
			LocalDate today) {
		Set<LocalDate> checkedDates = new HashSet<>();
		for (OffsetDateTime reportTime : reportTimes) {
			checkedDates.add(reportTime.toInstant().atZone(zoneId).toLocalDate());
		}
		LocalDate cursor = checkedDates.contains(today)
				? today
				: today.minusDays(1);
		int days = 0;
		while (checkedDates.contains(cursor)) {
			days++;
			cursor = cursor.minusDays(1);
		}
		return days;
	}

	private YearMonth parseMonth(String value, YearMonth current) {
		if (value == null || value.isBlank()) return current;
		try {
			return YearMonth.parse(value.trim());
		}
		catch (DateTimeParseException exception) {
			throw new BusinessException("PROFILE_MONTH_INVALID", "month 必须使用 yyyy-MM");
		}
	}

	private SignedAvatar signAvatar(String objectKey) {
		if (objectKey == null || objectKey.isBlank() || !storage.available()) {
			return new SignedAvatar(null, null);
		}
		try {
			URI uri = storage.signGetUrl(objectKey, storageProperties.getSignedUrlTtl());
			return new SignedAvatar(
					uri.toString(),
					clock.instant().plus(storageProperties.getSignedUrlTtl()));
		}
		catch (BusinessException exception) {
			return new SignedAvatar(null, null);
		}
	}

	private String displayName(UserAccount user) {
		if (user.nickname() != null && !user.nickname().isBlank()) return user.nickname();
		String username = user.username() == null ? "" : user.username();
		int at = username.indexOf('@');
		return at > 0 ? username.substring(0, at) : "UniSpeaking User";
	}

	private record SignedAvatar(String url, Instant expiresAt) {}
}
