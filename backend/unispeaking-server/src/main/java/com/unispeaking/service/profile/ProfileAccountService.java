package com.unispeaking.service.profile;

import com.unispeaking.domain.dto.profile.AvatarResponse;
import com.unispeaking.domain.dto.profile.UpdateProfileRequest;
import com.unispeaking.domain.dto.profile.UpdateProfileResponse;

public interface ProfileAccountService {
	/** Updates the user's public nickname. */
	UpdateProfileResponse updateNickname(String userId, UpdateProfileRequest request);

	/** Replaces the user's avatar with the supplied file content. */
	AvatarResponse replaceAvatar(String userId, byte[] content);
}
