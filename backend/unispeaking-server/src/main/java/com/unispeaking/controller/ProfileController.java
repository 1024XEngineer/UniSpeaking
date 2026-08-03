package com.unispeaking.controller;

import com.unispeaking.common.response.ApiResponse;
import com.unispeaking.domain.dto.profile.AvatarResponse;
import com.unispeaking.domain.dto.profile.ProfileOverviewResponse;
import com.unispeaking.domain.dto.profile.UpdateProfileRequest;
import com.unispeaking.domain.dto.profile.UpdateProfileResponse;
import com.unispeaking.service.auth.AuthService;
import com.unispeaking.service.profile.ProfileAccountService;
import com.unispeaking.service.profile.ProfileOverviewService;
import jakarta.validation.Valid;
import java.io.IOException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {
	private final AuthService authService;
	private final ProfileOverviewService overviewService;
	private final ProfileAccountService accountService;

	public ProfileController(
			AuthService authService,
			ProfileOverviewService overviewService,
			ProfileAccountService accountService) {
		this.authService = authService;
		this.overviewService = overviewService;
		this.accountService = accountService;
	}

	@GetMapping("/overview")
	public ApiResponse<ProfileOverviewResponse> overview(
			@RequestParam(required = false) String month) {
		return ApiResponse.success(overviewService.getOverview(
				authService.requireUserId(null), month));
	}

	@PatchMapping
	public ApiResponse<UpdateProfileResponse> update(
			@Valid @RequestBody UpdateProfileRequest request) {
		return ApiResponse.success(accountService.updateNickname(
				authService.requireUserId(null), request));
	}

	@PostMapping("/avatar")
	public ApiResponse<AvatarResponse> avatar(
			@RequestPart("avatar") MultipartFile avatar) throws IOException {
		return ApiResponse.success(accountService.replaceAvatar(
				authService.requireUserId(null), avatar.getBytes()));
	}
}
