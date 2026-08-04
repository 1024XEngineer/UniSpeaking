package com.unispeaking.controller;

import com.unispeaking.common.response.ApiResponse;
import com.unispeaking.domain.dto.achievement.AchievementAcknowledgeRequest;
import com.unispeaking.domain.dto.achievement.AchievementAcknowledgeResponse;
import com.unispeaking.domain.dto.achievement.AchievementOverviewResponse;
import com.unispeaking.domain.dto.achievement.AchievementSyncResponse;
import com.unispeaking.service.achievement.AchievementService;
import com.unispeaking.service.auth.AuthService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AchievementController {

	private final AuthService authService;
	private final AchievementService achievementService;

	public AchievementController(
			AuthService authService,
			AchievementService achievementService) {
		this.authService = authService;
		this.achievementService = achievementService;
	}

	@GetMapping("/achievements")
	public ApiResponse<AchievementOverviewResponse> overview() {
		return ApiResponse.success(
				achievementService.getOverview(currentUserId()));
	}

	@PostMapping("/achievement-unlocks")
	public ApiResponse<AchievementSyncResponse> synchronize() {
		return ApiResponse.success(
				achievementService.synchronize(currentUserId()));
	}

	@PatchMapping("/achievement-unlocks/{achievementId}")
	public ApiResponse<AchievementAcknowledgeResponse> acknowledge(
			@PathVariable String achievementId,
			@RequestBody AchievementAcknowledgeRequest request) {
		return ApiResponse.success(achievementService.acknowledge(
				currentUserId(),
				achievementId,
				request));
	}

	private UUID currentUserId() {
		return UUID.fromString(authService.requireUserId(null));
	}
}
