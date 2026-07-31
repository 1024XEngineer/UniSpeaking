package com.unispeaking.service.profile.impl;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.domain.dto.profile.ProfileOverviewResponse;
import com.unispeaking.domain.po.auth.UserAccount;
import com.unispeaking.infrastructure.config.ObjectStorageProperties;
import com.unispeaking.infrastructure.config.ProfileProperties;
import com.unispeaking.infrastructure.persistence.repository.evaluation.SessionEvaluationRepository;
import com.unispeaking.infrastructure.persistence.repository.scene.SceneRepository;
import com.unispeaking.infrastructure.persistence.repository.user.UserAccountRepository;
import com.unispeaking.infrastructure.storage.ObjectStorageProvider;
import com.unispeaking.service.profile.ProfileOverviewService;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ProfileOverviewServiceImpl implements ProfileOverviewService {
	private final UserAccountRepository accounts;
	private final SceneRepository scenes;
	private final SessionEvaluationRepository evaluations;
	private final ObjectStorageProvider storage;
	private final ObjectStorageProperties storageProperties;
	private final ZoneId zoneId;

	public ProfileOverviewServiceImpl(
			UserAccountRepository accounts,
			SceneRepository scenes,
			SessionEvaluationRepository evaluations,
			ObjectStorageProvider storage,
			ObjectStorageProperties storageProperties,
			ProfileProperties profileProperties) {
		this.accounts = accounts;
		this.scenes = scenes;
		this.evaluations = evaluations;
		this.storage = storage;
		this.storageProperties = storageProperties;
		this.zoneId = profileProperties.zoneId();
	}

	@Override
	public ProfileOverviewResponse getOverview(String userId, String requestedMonth) {
		UUID id = UUID.fromString(userId);
		UserAccount user = accounts.findById(id)
				.orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "用户不存在"));
		YearMonth current = YearMonth.now(zoneId);
		YearMonth month = parseMonth(requestedMonth, current);
		if (month.isAfter(current)) {
			throw new BusinessException("PROFILE_MONTH_INVALID", "不能查看未来月份");
		}
		Instant start = month.atDay(1).atStartOfDay(zoneId).toInstant();
		Instant end = month.plusMonths(1).atDay(1).atStartOfDay(zoneId).toInstant();
		List<LocalDate> dates = evaluations.findCreatedAtBySceneIdsBetween(
						scenes.findAllIdsByUserId(userId),
						start.atOffset(ZoneOffset.UTC),
						end.atOffset(ZoneOffset.UTC))
				.stream()
				.map(value -> value.toInstant().atZone(zoneId).toLocalDate())
				.distinct()
				.sorted()
				.toList();
		SignedAvatar signed = signAvatar(user.avatarObjectKey());
		String displayName = displayName(user);
		return new ProfileOverviewResponse(
				new ProfileOverviewResponse.Account(
						user.id(), user.username(), user.nickname(), displayName,
						signed.url(), signed.expiresAt()),
				new ProfileOverviewResponse.Calendar(
						month.toString(), dates, dates.contains(LocalDate.now(zoneId))));
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
					Instant.now().plus(storageProperties.getSignedUrlTtl()));
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
