package com.unispeaking.service.account;

import com.unispeaking.domain.dto.account.AccountProfileResponse;
import com.unispeaking.domain.dto.account.AvatarResponse;
import java.util.UUID;

public interface AccountService {

	AccountProfileResponse updateNickname(UUID userId, String nickname);

	AvatarResponse uploadAvatar(
			UUID userId,
			String originalFilename,
			String contentType,
			byte[] bytes);

	void deleteAvatar(UUID userId);
}
