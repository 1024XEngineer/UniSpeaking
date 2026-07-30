package com.unispeaking.controller;

import com.unispeaking.domain.dto.account.AccountProfileResponse;
import com.unispeaking.domain.dto.account.AvatarResponse;
import com.unispeaking.domain.dto.account.DeleteAccountRequest;
import com.unispeaking.domain.dto.account.UpdateAccountProfileRequest;
import com.unispeaking.domain.dto.response.ApiResponse;
import com.unispeaking.exception.BusinessException;
import com.unispeaking.service.account.AccountService;
import com.unispeaking.service.auth.AuthService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/account")
public class AccountController {

	private static final long MAX_AVATAR_BYTES = 2L * 1024 * 1024;

	private final AuthService authService;
	private final AccountService accountService;

	public AccountController(AuthService authService, AccountService accountService) {
		this.authService = authService;
		this.accountService = accountService;
	}

	@PatchMapping("/profile")
	public ApiResponse<AccountProfileResponse> updateProfile(
			@Valid @RequestBody UpdateAccountProfileRequest request) {
		return ApiResponse.success(accountService.updateNickname(
				currentUserId(),
				request.nickname()));
	}

	@PostMapping("/avatar")
	public ApiResponse<AvatarResponse> uploadAvatar(
			@RequestPart("avatar") MultipartFile avatar) {
		if (avatar.getSize() > MAX_AVATAR_BYTES) {
			throw new BusinessException(
					"AVATAR_TOO_LARGE",
					"头像不能超过 2 MiB");
		}
		try {
			return ApiResponse.success(accountService.uploadAvatar(
					currentUserId(),
					avatar.getOriginalFilename(),
					avatar.getContentType(),
					avatar.getBytes()));
		}
		catch (IOException exception) {
			throw new BusinessException(
					"INVALID_AVATAR_FILE",
					"无法读取头像文件");
		}
	}

	@DeleteMapping("/avatar")
	public ApiResponse<Void> deleteAvatar() {
		accountService.deleteAvatar(currentUserId());
		return ApiResponse.success(null);
	}

	@DeleteMapping
	public ApiResponse<Void> requestDeletion(
			@Valid @RequestBody DeleteAccountRequest request) {
		accountService.requestDeletion(currentUserId(), request.currentPassword());
		return ApiResponse.success(null);
	}

	private UUID currentUserId() {
		return UUID.fromString(authService.requireUserId(null));
	}
}
