package com.unispeaking.service.profile;

import com.unispeaking.domain.dto.profile.AvatarResponse;
import com.unispeaking.domain.dto.profile.UpdateProfileRequest;
import com.unispeaking.domain.dto.profile.UpdateProfileResponse;

public interface ProfileAccountService {
	UpdateProfileResponse updateNickname(String userId, UpdateProfileRequest request);
	AvatarResponse replaceAvatar(String userId, byte[] content);
}
