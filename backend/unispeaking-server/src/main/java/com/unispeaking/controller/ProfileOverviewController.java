package com.unispeaking.controller;

import com.unispeaking.domain.dto.profile.ProfileOverviewResponse;
import com.unispeaking.domain.dto.response.ApiResponse;
import com.unispeaking.exception.BusinessException;
import com.unispeaking.service.auth.AuthService;
import com.unispeaking.service.profile.ProfileOverviewService;
import com.unispeaking.service.profile.query.AchievementMetricQueryPort;
import com.unispeaking.service.profile.query.LearningAssetCountPort;
import com.unispeaking.service.profile.query.LearningStatisticsQueryPort;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
@ConditionalOnBean({
	LearningStatisticsQueryPort.class,
	LearningAssetCountPort.class,
	AchievementMetricQueryPort.class
})
public class ProfileOverviewController {

	private final AuthService authService;
	private final ProfileOverviewService profileOverviewService;

	public ProfileOverviewController(
			AuthService authService,
			ProfileOverviewService profileOverviewService) {
		this.authService = authService;
		this.profileOverviewService = profileOverviewService;
	}

	@GetMapping("/overview")
	public ApiResponse<ProfileOverviewResponse> get(
			@RequestParam(required = false) String yearMonth) {
		UUID userId = UUID.fromString(authService.requireUserId(null));
		return ApiResponse.success(profileOverviewService.getOverview(
				userId,
				parseYearMonth(yearMonth)));
	}

	private YearMonth parseYearMonth(String value) {
		if (value == null) {
			return null;
		}
		try {
			return YearMonth.parse(value);
		}
		catch (DateTimeParseException exception) {
			throw new BusinessException(
					"VALIDATION_ERROR",
					"yearMonth 必须使用 yyyy-MM 格式");
		}
	}
}
