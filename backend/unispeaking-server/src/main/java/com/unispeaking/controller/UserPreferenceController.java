package com.unispeaking.controller;

import com.unispeaking.domain.dto.profile.UpdateUserPreferenceRequest;
import com.unispeaking.domain.dto.profile.UserPreferenceResponse;
import com.unispeaking.domain.dto.response.ApiResponse;
import com.unispeaking.service.auth.AuthService;
import com.unispeaking.service.profile.ProfileService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user-preferences")
public class UserPreferenceController {

	private final AuthService authService;
	private final ProfileService profileService;

	public UserPreferenceController(AuthService authService, ProfileService profileService) {
		this.authService = authService;
		this.profileService = profileService;
	}

	@GetMapping
	public ApiResponse<UserPreferenceResponse> get() {
		String userId = authService.requireUserId(null);
		return ApiResponse.success(profileService.getPreference(userId));
	}

	@PutMapping
	public ApiResponse<UserPreferenceResponse> update(
			@Valid @RequestBody UpdateUserPreferenceRequest request) {
		String userId = authService.requireUserId(null);
		return ApiResponse.success(profileService.updatePreference(userId, request));
	}
}
